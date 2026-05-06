const authPanel = document.getElementById("authPanel");
const chatPanel = document.getElementById("chatPanel");
const loginForm = document.getElementById("loginForm");
const messageForm = document.getElementById("messageForm");
const messagesEl = document.getElementById("messages");
const authErrorEl = document.getElementById("authError");
const sessionInfoEl = document.getElementById("sessionInfo");
const socketDotEl = document.getElementById("socketDot");
const socketTextEl = document.getElementById("socketText");
const logoutBtn = document.getElementById("logoutBtn");
const typingIndicatorEl = document.getElementById("typingIndicator");
const messageInputEl = document.getElementById("messageInput");
const simulateFailBtn = document.getElementById("simulateFailBtn");
const simulateRestoreBtn = document.getElementById("simulateRestoreBtn");
const simulationStatusEl = document.getElementById("simulationStatus");
const presenceCountEl = document.getElementById("presenceCount");
const presenceListEl = document.getElementById("presenceList");
const loadMoreBtn = document.getElementById("loadMoreBtn");
const loadMoreStatusEl = document.getElementById("loadMoreStatus");

const SESSION_STORAGE_KEY = "superchat.session";
const TYPING_STOP_DELAY_MS = 1400;
const PAGE_SIZE = 50;
const KEYCLOAK_TOKEN_URL = "/kc/realms/superchat/protocol/openid-connect/token";
const KEYCLOAK_CLIENT_ID = "superchat-frontend";

const state = {
  token: "",
  refreshToken: "",
  tokenExpiresAt: 0,
  username: "",
  displayName: "",
  conversationId: 1,
  activeConversationId: null,
  conversations: [],
  stompClient: null,
  isTyping: false,
  typingStopTimer: null,
  remoteTypingTimers: new Map(),
  remoteTypingUsers: new Set(),
  currentPage: 0,
  totalPages: 0,
  isLoadingMessages: false,
  optimisticMessages: new Map(),
  presenceInterval: null,
  msgSub: null,
  typingSub: null,
  pendingAttachment: null, // { file, uploadUrl, publicUrl, attachmentType }
  presignInProgress: false,
  viewOnce: false,
};

// ── Token management ─────────────────────────────────────────────────────────

function decodeJwtPayload(token) {
  try {
    return JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return null;
  }
}

async function refreshAccessToken() {
  if (!state.refreshToken) return false;
  try {
    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: KEYCLOAK_CLIENT_ID,
      refresh_token: state.refreshToken,
    });
    const response = await fetch(KEYCLOAK_TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
    if (!response.ok) return false;
    const data = await response.json();
    applyTokenResponse(data);
    saveSession();
    return true;
  } catch {
    return false;
  }
}

function applyTokenResponse(data) {
  state.token = data.access_token;
  state.refreshToken = data.refresh_token || state.refreshToken;
  // 30s early refresh buffer
  state.tokenExpiresAt = Date.now() + (data.expires_in * 1000) - 30000;

  const payload = decodeJwtPayload(data.access_token);
  if (payload) {
    state.username = payload.sub || "";
    state.displayName = payload.preferred_username || payload.sub || "";
  }
}

// ── Session persistence ───────────────────────────────────────────────────────

function saveSession() {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({
    token: state.token,
    refreshToken: state.refreshToken,
    tokenExpiresAt: state.tokenExpiresAt,
    username: state.username,
    displayName: state.displayName,
    conversationId: state.conversationId,
  }));
}

function clearSession() {
  localStorage.removeItem(SESSION_STORAGE_KEY);
}

// ── UI helpers ────────────────────────────────────────────────────────────────

function setPanels(isAuthenticated) {
  authPanel.classList.toggle("hidden", isAuthenticated);
  chatPanel.classList.toggle("hidden", !isAuthenticated);
}

function setSessionInfo() {
  const display = state.displayName || state.username;
  sessionInfoEl.textContent = `Usuario: ${display} | Conversación: ${state.conversationId}`;
}

function setSocketStatus(online) {
  socketDotEl.classList.toggle("online", online);
  socketDotEl.classList.toggle("offline", !online);
  socketTextEl.textContent = online ? "conectado" : "desconectado";
}

function disconnectWebSocket() {
  if (state.stompClient) {
    state.stompClient.deactivate();
    state.stompClient = null;
  }
  setSocketStatus(false);
}

function resetState() {
  state.token = "";
  state.refreshToken = "";
  state.tokenExpiresAt = 0;
  state.username = "";
  state.displayName = "";
  state.conversationId = 1;
  state.currentPage = 0;
  state.totalPages = 0;
  state.optimisticMessages.clear();
  clearInterval(state.presenceInterval);
  state.presenceInterval = null;
}

function logout() {
  sendTypingEvent(false);
  disconnectWebSocket();
  clearSession();
  resetState();
  messagesEl.innerHTML = "";
  sessionInfoEl.textContent = "";
  if (simulationStatusEl) simulationStatusEl.textContent = "Estado publicador: desconocido";
  renderPresence([]);
  renderTypingIndicator();
  loginForm.reset();
  setPanels(false);
}

// ── API helper ────────────────────────────────────────────────────────────────

async function api(url, options = {}) {
  // Auto-refresh token before expiry
  if (state.token && Date.now() > state.tokenExpiresAt) {
    const ok = await refreshAccessToken();
    if (!ok) { logout(); throw new Error("Session expired"); }
  }

  const headers = { ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, { ...options, headers });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.json();
}

// ── Presence ──────────────────────────────────────────────────────────────────

function renderPresence(users) {
  if (!presenceCountEl || !presenceListEl) return;

  const normalized = (users || [])
    .filter(u => typeof u === "string" && u.trim().length > 0)
    .map(u => u.trim());
  const unique = [...new Set(normalized)].sort((a, b) => a.localeCompare(b, "es", { sensitivity: "base" }));

  presenceCountEl.textContent = `${unique.length} usuarios`;
  presenceListEl.innerHTML = "";

  if (unique.length === 0) {
    const empty = document.createElement("li");
    empty.textContent = "Sin usuarios conectados";
    presenceListEl.appendChild(empty);
    return;
  }

  unique.forEach(username => {
    const item = document.createElement("li");
    item.textContent = username;
    item.style.cursor = 'pointer';
    item.addEventListener('click', () => {
      const dmModalEl = document.getElementById('dmModal');
      const dmSearchEl = document.getElementById('dmSearch');
      if (dmModalEl && dmSearchEl) {
        dmModalEl.classList.remove('hidden');
        dmSearchEl.value = username;
        dmSearchEl.dispatchEvent(new Event('input'));
      }
    });
    presenceListEl.appendChild(item);
  });
}

async function refreshPresence() {
  if (!state.token) { renderPresence([]); return; }
  try {
    const data = await api("/api/chat/presence", { method: "GET" });
    renderPresence(data.users || []);
  } catch {
    renderPresence([]);
  }
}

// ── Simulation ────────────────────────────────────────────────────────────────

function setSimulationStatusText(running) {
  if (!simulationStatusEl) return;
  if (running === true) { simulationStatusEl.textContent = "Estado publicador: activo"; return; }
  if (running === false) { simulationStatusEl.textContent = "Estado publicador: caído (cola acumulando mensajes)"; return; }
  simulationStatusEl.textContent = "Estado publicador: desconocido";
}

async function refreshSimulationStatus() {
  if (!state.token) { setSimulationStatusText(undefined); return; }
  try {
    const data = await api("/api/chat/simulation/realtime-publisher/status", { method: "GET" });
    setSimulationStatusText(Boolean(data.running));
  } catch {
    setSimulationStatusText(undefined);
  }
}

async function simulatePublisherFailure() {
  try {
    await api("/api/chat/simulation/realtime-publisher/fail", { method: "POST" });
    setSimulationStatusText(false);
  } catch { setSimulationStatusText(undefined); }
}

async function simulatePublisherRestore() {
  try {
    await api("/api/chat/simulation/realtime-publisher/restore", { method: "POST" });
    setSimulationStatusText(true);
  } catch { setSimulationStatusText(undefined); }
}

// ── Messages ──────────────────────────────────────────────────────────────────

function resolveDisplayName(message) {
  if (message.senderName) return message.senderName;
  const sender = message.sender || "system";
  return (sender === state.username && state.displayName) ? state.displayName : sender;
}

function appendMessage(message, isOptimistic = false) {
  // Deduplicate: if a confirmed optimistic message matches this real one, skip the duplicate
  if (!isOptimistic && message.sender === state.username) {
    const confirmed = messagesEl.querySelector("[data-confirmed]");
    if (confirmed && confirmed.dataset.content === message.content) {
      confirmed.removeAttribute("data-confirmed");
      confirmed.removeAttribute("data-content");
      return;
    }
  }

  const item = document.createElement("div");
  item.className = "msg" + (isOptimistic ? " msg-optimistic" : "");
  if (message.optimisticId) item.id = `msg-${message.optimisticId}`;

  const meta = document.createElement("div");
  meta.className = "meta";
  const created = message.createdAt ? new Date(message.createdAt).toLocaleTimeString() : "";
  const status = isOptimistic ? " (enviando...)" : "";
  meta.textContent = `${resolveDisplayName(message)} ${created}${status}`.trim();

  // View-once expired tombstone
  if (message.viewOnceExpired) {
    const tombstone = document.createElement("div");
    tombstone.className = "msg-view-once-expired";
    tombstone.textContent = "🔥 Mensaje visto — ya no está disponible";
    item.append(meta, tombstone);
    messagesEl.appendChild(item);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return;
  }

  const content = document.createElement("div");
  content.textContent = message.content || "";

  // View-once badge for fresh view-once messages
  if (message.viewOnce && !message.viewOnceExpired) {
    const badge = document.createElement("span");
    badge.className = "view-once-badge";
    badge.textContent = "🔥";
    content.appendChild(badge);
  }

  item.append(meta, content);

  if (message.attachmentType === 'IMAGE' && message.attachmentUrl) {
    const img = document.createElement('img');
    img.src = message.attachmentUrl;
    img.className = 'msg-image';
    img.loading = 'lazy';
    item.appendChild(img);
  } else if (message.attachmentType === 'AUDIO' && message.attachmentUrl) {
    const audio = document.createElement('audio');
    audio.src = message.attachmentUrl;
    audio.controls = true;
    audio.className = 'msg-audio';
    item.appendChild(audio);
  } else if (message.attachmentType === 'FILE' && message.attachmentUrl) {
    const link = document.createElement('a');
    link.href = message.attachmentUrl;
    link.textContent = '📎 Descargar archivo';
    link.className = 'msg-file';
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    item.appendChild(link);
  }

  messagesEl.appendChild(item);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function scrollMessagesToBottom() {
  requestAnimationFrame(() => { messagesEl.scrollTop = messagesEl.scrollHeight; });
}

async function ensureConversation() {
  try {
    const response = await api("/api/chat/conversations", {
      method: "POST",
      body: JSON.stringify({ name: "General" }),
    });
    state.conversationId = response.id;
  } catch {
    state.conversationId = 1;
  }
}

async function loadHistory() {
  messagesEl.innerHTML = "";
  state.currentPage = 0;
  state.optimisticMessages.clear();
  await loadMoreMessages();
}

async function loadMoreMessages() {
  if (state.isLoadingMessages || state.currentPage >= state.totalPages && state.totalPages > 0) {
    if (loadMoreBtn) loadMoreBtn.classList.add("hidden");
    return;
  }

  state.isLoadingMessages = true;
  if (loadMoreStatusEl) loadMoreStatusEl.textContent = "Cargando...";

  try {
    const convId = state.activeConversationId ?? state.conversationId;
    const data = await api(`/api/chat/conversations/${convId}/messages?page=${state.currentPage}&size=${PAGE_SIZE}`, { method: "GET" });
    state.totalPages = data.totalPages || 1;
    if (data.messages) {
      data.messages.forEach(msg => appendMessage(msg, false));
      state.currentPage++;
    }
    if (loadMoreBtn) {
      loadMoreBtn.classList.toggle("hidden", state.currentPage >= state.totalPages);
    }
  } catch (err) {
    if (loadMoreStatusEl) loadMoreStatusEl.textContent = "Error al cargar mensajes";
  } finally {
    state.isLoadingMessages = false;
    if (loadMoreStatusEl) loadMoreStatusEl.textContent = "";
    scrollMessagesToBottom();
  }
}

// ── Conversations ─────────────────────────────────────────────────────────────

async function loadConversations() {
  try {
    const list = await api("/api/chat/conversations", { method: "GET" });
    state.conversations = Array.isArray(list) ? list : [];
    renderConvList();
  } catch (e) {
    console.warn('Could not load conversations', e);
  }
}

function renderConvList() {
  const ul = document.getElementById('convList');
  if (!ul) return;
  ul.innerHTML = '';
  state.conversations.forEach(conv => {
    const li = document.createElement('li');
    const label = conv.type === 'DIRECT'
      ? `\u{1F4AC} ${conv.otherParticipantName || conv.id}`
      : `\u{1F465} ${conv.name || conv.id}`;
    li.textContent = label;
    li.title = label;
    li.dataset.id = String(conv.id);
    const activeId = state.activeConversationId ?? state.conversationId;
    if (conv.id === activeId) li.classList.add('active');
    li.addEventListener('click', () => switchConversation(conv.id, label));
    ul.appendChild(li);
  });
}

async function switchConversation(id, label) {
  state.activeConversationId = id;
  if (state.stompClient?.connected) {
    subscribeToConversation(id);
  }
  const headerEl = document.querySelector('.chat-header h2');
  if (headerEl) headerEl.textContent = label || `Conversación #${id}`;
  const messagesEl = document.getElementById('messages');
  if (messagesEl) messagesEl.innerHTML = '';
  state.currentPage = 0;
  state.totalPages = 0;
  state.optimisticMessages.clear();
  renderConvList();
  await loadMoreMessages();
}

// ── Typing ────────────────────────────────────────────────────────────────────

function renderTypingIndicator() {
  const users = [...state.remoteTypingUsers].filter(u => u && u !== state.username);
  if (users.length === 0) {
    typingIndicatorEl.classList.add("hidden");
    typingIndicatorEl.textContent = "";
    return;
  }
  typingIndicatorEl.classList.remove("hidden");
  typingIndicatorEl.textContent = users.length === 1
    ? `${users[0]} está escribiendo...`
    : `${users.length} personas están escribiendo...`;
}

function clearRemoteTypingState() {
  state.remoteTypingTimers.forEach(id => clearTimeout(id));
  state.remoteTypingTimers.clear();
  state.remoteTypingUsers.clear();
  renderTypingIndicator();
}

function publishTyping(typing) {
  if (!state.stompClient?.connected || !state.username) return;
  state.stompClient.publish({
    destination: "/app/typing",
    body: JSON.stringify({ conversationId: state.activeConversationId ?? state.conversationId, username: state.username, typing }),
  });
}

function sendTypingEvent(typing) {
  if (typing === state.isTyping) return;
  state.isTyping = typing;
  publishTyping(typing);
}

function scheduleStopTyping() {
  if (state.typingStopTimer) clearTimeout(state.typingStopTimer);
  state.typingStopTimer = setTimeout(() => sendTypingEvent(false), TYPING_STOP_DELAY_MS);
}

function handleTypingEvent(event) {
  if (!event || event.conversationId !== (state.activeConversationId ?? state.conversationId) || !event.username || event.username === state.username) return;
  if (!event.typing) {
    const id = state.remoteTypingTimers.get(event.username);
    if (id) { clearTimeout(id); state.remoteTypingTimers.delete(event.username); }
    state.remoteTypingUsers.delete(event.username);
    renderTypingIndicator();
    return;
  }
  state.remoteTypingUsers.add(event.username);
  const existing = state.remoteTypingTimers.get(event.username);
  if (existing) clearTimeout(existing);
  const timerId = setTimeout(() => {
    state.remoteTypingUsers.delete(event.username);
    state.remoteTypingTimers.delete(event.username);
    renderTypingIndicator();
  }, TYPING_STOP_DELAY_MS + 800);
  state.remoteTypingTimers.set(event.username, timerId);
  renderTypingIndicator();
}

// ── WebSocket ─────────────────────────────────────────────────────────────────

function subscribeToConversation(conversationId) {
  state.msgSub?.unsubscribe();
  state.typingSub?.unsubscribe();
  state.msgSub = state.stompClient.subscribe(`/topic/conversations.${conversationId}`, frame => {
    appendMessage(JSON.parse(frame.body));
  });
  state.typingSub = state.stompClient.subscribe(`/topic/conversations.${conversationId}.typing`, frame => {
    handleTypingEvent(JSON.parse(frame.body));
  });
}

function connectWebSocket() {
  disconnectWebSocket();
  clearRemoteTypingState();

  const socket = new SockJS("/ws");
  const client = new StompJs.Client({
    webSocketFactory: () => socket,
    connectHeaders: {
      username: state.username,
      displayName: state.displayName || state.username,
    },
    reconnectDelay: 5000,
    onConnect: () => {
      setSocketStatus(true);
      subscribeToConversation(state.activeConversationId ?? state.conversationId);
      client.subscribe(`/topic/presence`, frame => {
        const snapshot = JSON.parse(frame.body);
        renderPresence(snapshot.users || []);
      });
      refreshPresence();
      if (state.presenceInterval) clearInterval(state.presenceInterval);
      state.presenceInterval = setInterval(() => { if (state.token) refreshPresence(); }, 10000);
    },
    onStompError: () => setSocketStatus(false),
    onWebSocketClose: () => { setSocketStatus(false); clearInterval(state.presenceInterval); },
  });

  state.stompClient = client;
  client.activate();
}

// ── Event listeners ───────────────────────────────────────────────────────────

loginForm.addEventListener("submit", async event => {
  event.preventDefault();
  authErrorEl.textContent = "";

  const username = document.getElementById("phone").value.trim();
  const password = document.getElementById("password").value;

  try {
    const body = new URLSearchParams({
      grant_type: "password",
      client_id: KEYCLOAK_CLIENT_ID,
      username,
      password,
    });
    const response = await fetch(KEYCLOAK_TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
    if (!response.ok) throw new Error("Login failed");
    applyTokenResponse(await response.json());

    await ensureConversation();
    setSessionInfo();
    setPanels(true);
    await loadHistory();
    await loadConversations();
    await refreshSimulationStatus();
    connectWebSocket();
    saveSession();
  } catch {
    authErrorEl.textContent = "No fue posible iniciar sesión. Verifica usuario y contraseña.";
  }
});

messageForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (state.presignInProgress) return; // wait for presign to complete
  const content = messageInputEl.value.trim();
  if (!content && !state.pendingAttachment) return;

  const attachmentPreview = document.getElementById('attachmentPreview');
  const fileInput = document.getElementById('fileInput');

  let attachmentUrl = '';
  let attachmentType = '';
  if (state.pendingAttachment) {
    const { file, uploadUrl, publicUrl, attachmentType: aType } = state.pendingAttachment;
    try {
      const putResp = await fetch(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file,
      });
      if (!putResp.ok) throw new Error('Upload failed: ' + putResp.status);
      attachmentUrl = publicUrl;
      attachmentType = aType;
    } catch (e) {
      alert('Error al subir el archivo: ' + e.message);
      state.viewOnce = false;
      const viewOnceBtn = document.getElementById('viewOnceBtn');
      if (viewOnceBtn) {
        viewOnceBtn.classList.remove('active');
        viewOnceBtn.setAttribute('aria-pressed', 'false');
      }
      return;
    }
    state.pendingAttachment = null;
    attachmentPreview.classList.add('hidden');
    attachmentPreview.innerHTML = '';
    fileInput.value = '';
  }

  const viewOnce = state.viewOnce;
  const optimisticId = `temp-${Date.now()}`;
  appendMessage({ optimisticId, sender: state.username, senderName: state.displayName, content, attachmentUrl, attachmentType, viewOnce, viewOnceExpired: false, createdAt: new Date().toISOString() }, true);
  state.optimisticMessages.set(optimisticId, true);
  messageInputEl.value = "";

  // Reset view-once state
  state.viewOnce = false;
  const viewOnceBtn = document.getElementById('viewOnceBtn');
  if (viewOnceBtn) {
    viewOnceBtn.classList.remove('active');
    viewOnceBtn.setAttribute('aria-pressed', 'false');
  }

  try {
    sendTypingEvent(false);
    await api("/api/chat/messages", {
      method: "POST",
      body: JSON.stringify({ conversationId: state.activeConversationId ?? state.conversationId, content, attachmentUrl, attachmentType, viewOnce }),
    });
    state.optimisticMessages.delete(optimisticId);
    const confirmedEl = document.getElementById(`msg-${optimisticId}`);
    if (confirmedEl) {
      confirmedEl.classList.remove("msg-optimistic");
      confirmedEl.dataset.confirmed = "true";
      confirmedEl.dataset.content = content;
      const metaEl = confirmedEl.querySelector(".meta");
      if (metaEl) metaEl.textContent = metaEl.textContent.replace(" (enviando...)", "");
    }
  } catch {
    const el = document.getElementById(`msg-${optimisticId}`);
    if (el) { el.classList.add("msg-error"); el.title = "Falló al enviar. Intenta de nuevo."; }
  }
});

messageInputEl.addEventListener("input", () => {
  const hasContent = messageInputEl.value.trim().length > 0;
  if (!hasContent) { sendTypingEvent(false); return; }
  sendTypingEvent(true);
  scheduleStopTyping();
});

logoutBtn?.addEventListener("click", logout);
simulateFailBtn?.addEventListener("click", simulatePublisherFailure);
simulateRestoreBtn?.addEventListener("click", simulatePublisherRestore);
loadMoreBtn?.addEventListener("click", loadMoreMessages);

// ── Emoji picker ──────────────────────────────────────────────────────────────

const emojiPickerContainer = document.getElementById('emojiPicker');
const picker = new EmojiMart.Picker({
  locale: 'es',
  onEmojiSelect: (emoji) => {
    const input = document.getElementById('messageInput');
    const start = input.selectionStart;
    const end = input.selectionEnd;
    const text = input.value;
    input.value = text.slice(0, start) + emoji.native + text.slice(end);
    input.selectionStart = input.selectionEnd = start + emoji.native.length;
    input.focus();
    emojiPickerContainer.classList.add('hidden');
  }
});
emojiPickerContainer.appendChild(picker);

document.getElementById('emojiBtn').addEventListener('click', (e) => {
  e.stopPropagation();
  emojiPickerContainer.classList.toggle('hidden');
});

// ── View-once toggle ──────────────────────────────────────────────────────────

const viewOnceBtn = document.getElementById('viewOnceBtn');
viewOnceBtn.addEventListener('click', () => {
  state.viewOnce = !state.viewOnce;
  viewOnceBtn.classList.toggle('active', state.viewOnce);
  viewOnceBtn.setAttribute('aria-pressed', String(state.viewOnce));
});

// ── Attachment picker ─────────────────────────────────────────────────────────

const attachBtn = document.getElementById('attachBtn');
const fileInput = document.getElementById('fileInput');
const attachmentPreview = document.getElementById('attachmentPreview');

attachBtn.addEventListener('click', () => fileInput.click());

fileInput.addEventListener('change', async () => {
  const file = fileInput.files[0];
  if (!file) return;
  if (file.size > 50 * 1024 * 1024) {
    alert('El archivo no puede superar 50 MB.');
    fileInput.value = '';
    return;
  }

  state.presignInProgress = true;

  const contentType = file.type || 'application/octet-stream';
  try {
    // Auto-refresh token before expiry
    if (state.token && Date.now() > state.tokenExpiresAt) {
      const ok = await refreshAccessToken();
      if (!ok) { logout(); return; }
    }
    const presignResp = await fetch('/api/chat/attachments/presign', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.token}`,
      },
      body: JSON.stringify({ filename: file.name, contentType, conversationId: state.activeConversationId ?? state.conversationId }),
    });
    if (!presignResp.ok) throw new Error(await presignResp.text());
    const data = await presignResp.json();

    if (!data?.uploadUrl || !data?.publicUrl || !data?.attachmentType) {
      throw new Error('Respuesta de presign inválida del servidor');
    }

    state.pendingAttachment = { file, uploadUrl: data.uploadUrl, publicUrl: data.publicUrl, attachmentType: data.attachmentType };

    const oldImg = attachmentPreview.querySelector('img');
    if (oldImg && oldImg.src.startsWith('blob:')) URL.revokeObjectURL(oldImg.src);
    attachmentPreview.innerHTML = '';
    attachmentPreview.classList.remove('hidden');

    if (data.attachmentType === 'IMAGE') {
      const img = document.createElement('img');
      img.src = URL.createObjectURL(file);
      attachmentPreview.appendChild(img);
    } else {
      const label = document.createElement('span');
      label.textContent = `📎 ${file.name}`;
      attachmentPreview.appendChild(label);
    }

    const removeBtn = document.createElement('button');
    removeBtn.textContent = '✕';
    removeBtn.type = 'button';
    removeBtn.className = 'remove-attachment';
    removeBtn.onclick = () => {
      const oldImg = attachmentPreview.querySelector('img');
      if (oldImg && oldImg.src.startsWith('blob:')) URL.revokeObjectURL(oldImg.src);
      state.pendingAttachment = null;
      attachmentPreview.classList.add('hidden');
      attachmentPreview.innerHTML = '';
      fileInput.value = '';
    };
    attachmentPreview.appendChild(removeBtn);
    state.presignInProgress = false;
  } catch (e) {
    alert('Error al preparar el adjunto: ' + e.message);
    fileInput.value = '';
    state.presignInProgress = false;
  }
});

document.addEventListener('click', (e) => {
  if (!emojiPickerContainer.contains(e.target) && e.target !== document.getElementById('emojiBtn')) {
    emojiPickerContainer.classList.add('hidden');
  }
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    emojiPickerContainer.classList.add('hidden');
  }
});

// ── DM modal ──────────────────────────────────────────────────────────────────

const newDmBtn = document.getElementById('newDmBtn');
const dmModal = document.getElementById('dmModal');
const closeDmModalBtn = document.getElementById('closeDmModal');
const dmSearchInput = document.getElementById('dmSearch');
const dmUserList = document.getElementById('dmUserList');

if (newDmBtn) {
  newDmBtn.addEventListener('click', () => {
    dmModal.classList.remove('hidden');
    dmSearchInput.value = '';
    dmUserList.innerHTML = '';
    dmSearchInput.focus();
  });
}

if (closeDmModalBtn) {
  closeDmModalBtn.addEventListener('click', () => dmModal.classList.add('hidden'));
}

if (dmModal) {
  dmModal.addEventListener('click', (e) => {
    if (e.target === dmModal) dmModal.classList.add('hidden');
  });
}

let dmSearchTimer;
if (dmSearchInput) {
  dmSearchInput.addEventListener('input', () => {
    clearTimeout(dmSearchTimer);
    dmSearchTimer = setTimeout(async () => {
      const q = dmSearchInput.value.trim();
      if (!q) { dmUserList.innerHTML = ''; return; }
      try {
        const users = await api('/api/users/search?q=' + encodeURIComponent(q), { method: 'GET' });
        dmUserList.innerHTML = '';
        (Array.isArray(users) ? users : []).forEach(u => {
          const li = document.createElement('li');
          li.textContent = u.displayName || u.keycloakId;
          li.addEventListener('click', () => startDm(u.keycloakId, u.displayName));
          dmUserList.appendChild(li);
        });
      } catch (e) {
        console.warn('User search failed', e);
      }
    }, 300);
  });
}

async function startDm(participantId, participantName) {
  if (dmModal) dmModal.classList.add('hidden');
  try {
    const dm = await api('/api/chat/conversations/dm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ participantId, participantName }),
    });
    await loadConversations();
    await switchConversation(dm.id, `\u{1F4AC} ${dm.otherParticipantName || participantName}`);
  } catch (e) {
    alert('Error al crear DM: ' + (e?.message || String(e)));
  }
}

// ── Session restore ───────────────────────────────────────────────────────────

async function restoreSession() {
  const saved = localStorage.getItem(SESSION_STORAGE_KEY);
  if (!saved) return;

  try {
    const parsed = JSON.parse(saved);
    if (!parsed?.token || !parsed?.username) { clearSession(); return; }

    state.token = parsed.token;
    state.refreshToken = parsed.refreshToken || "";
    state.tokenExpiresAt = parsed.tokenExpiresAt || 0;
    state.username = parsed.username;
    state.displayName = parsed.displayName || parsed.username;
    if (Number.isInteger(parsed.conversationId) && parsed.conversationId > 0) {
      state.conversationId = parsed.conversationId;
    }

    // Check expiry; attempt refresh if expired
    if (Date.now() > state.tokenExpiresAt) {
      const ok = await refreshAccessToken();
      if (!ok) { logout(); return; }
    }

    await ensureConversation();
    setSessionInfo();
    setPanels(true);
    await loadHistory();
    await loadConversations();
    await refreshSimulationStatus();
    connectWebSocket();
    saveSession();
  } catch {
    logout();
  }
}

// ── Bootstrap ─────────────────────────────────────────────────────────────────

setSocketStatus(false);
setPanels(false);
renderPresence([]);
renderTypingIndicator();
restoreSession();
