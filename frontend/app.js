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
  stompClient: null,
  isTyping: false,
  typingStopTimer: null,
  remoteTypingTimers: new Map(),
  remoteTypingUsers: new Set(),
  currentPage: 0,
  totalPages: 0,
  isLoadingMessages: false,
  optimisticMessages: new Map(),
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

  unique.forEach(user => {
    const item = document.createElement("li");
    item.textContent = (user === state.username && state.displayName) ? state.displayName : user;
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

function appendMessage(message, isOptimistic = false) {
  const item = document.createElement("div");
  item.className = "msg" + (isOptimistic ? " msg-optimistic" : "");
  if (message.optimisticId) item.id = `msg-${message.optimisticId}`;

  const meta = document.createElement("div");
  meta.className = "meta";
  const created = message.createdAt ? new Date(message.createdAt).toLocaleTimeString() : "";
  const status = isOptimistic ? " (enviando...)" : "";
  const sender = message.sender || "system";
  const displaySender = (sender === state.username && state.displayName) ? state.displayName : sender;
  meta.textContent = `${displaySender} ${created}${status}`.trim();

  const content = document.createElement("div");
  content.textContent = message.content || "";

  item.append(meta, content);
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
    const data = await api(`/api/chat/conversations/${state.conversationId}/messages?page=${state.currentPage}&size=${PAGE_SIZE}`, { method: "GET" });
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
    body: JSON.stringify({ conversationId: state.conversationId, username: state.username, typing }),
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
  if (!event || event.conversationId !== state.conversationId || !event.username || event.username === state.username) return;
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
      client.subscribe(`/topic/conversations/${state.conversationId}`, frame => {
        appendMessage(JSON.parse(frame.body));
      });
      client.subscribe(`/topic/conversations/${state.conversationId}/typing`, frame => {
        handleTypingEvent(JSON.parse(frame.body));
      });
      client.subscribe(`/topic/presence`, frame => {
        const snapshot = JSON.parse(frame.body);
        renderPresence(snapshot.users || []);
      });
      refreshPresence();
    },
    onStompError: () => setSocketStatus(false),
    onWebSocketClose: () => setSocketStatus(false),
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
    await refreshSimulationStatus();
    connectWebSocket();
    saveSession();
  } catch {
    authErrorEl.textContent = "No fue posible iniciar sesión. Verifica usuario y contraseña.";
  }
});

messageForm.addEventListener("submit", async event => {
  event.preventDefault();
  const content = messageInputEl.value.trim();
  if (!content) return;

  const optimisticId = `temp-${Date.now()}`;
  appendMessage({ optimisticId, sender: state.username, content, createdAt: new Date().toISOString() }, true);
  state.optimisticMessages.set(optimisticId, true);
  messageInputEl.value = "";

  try {
    sendTypingEvent(false);
    await api("/api/chat/messages", {
      method: "POST",
      body: JSON.stringify({ conversationId: state.conversationId, content }),
    });
    state.optimisticMessages.delete(optimisticId);
    document.getElementById(`msg-${optimisticId}`)?.classList.remove("msg-optimistic");
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
