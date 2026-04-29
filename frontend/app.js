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

const state = {
  token: "",
  username: "",
  conversationId: 1,
  alias: "",
  phone: "",
  stompClient: null,
  presenceRefreshTimer: null,
  isTyping: false,
  typingStopTimer: null,
  remoteTypingTimers: new Map(),
  remoteTypingUsers: new Set(),
  currentPage: 0,
  totalPages: 0,
  isLoadingMessages: false,
  optimisticMessages: new Map(),
};

function setPanels(isAuthenticated) {
  authPanel.classList.toggle("hidden", isAuthenticated);
  chatPanel.classList.toggle("hidden", !isAuthenticated);
}

function setSessionInfo() {
  const display = state.alias ? `${state.alias} (${state.phone || state.username})` : state.phone || state.username;
  sessionInfoEl.textContent = `Usuario: ${display} | Conversacion: ${state.conversationId}`;
}

function saveSession() {
  const payload = {
    token: state.token,
    username: state.username,
    alias: state.alias,
    phone: state.phone,
    conversationId: state.conversationId,
  };
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(payload));
}

function clearSession() {
  localStorage.removeItem(SESSION_STORAGE_KEY);
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
  state.username = "";
  state.alias = "";
  state.phone = "";
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
  if (simulationStatusEl) {
    simulationStatusEl.textContent = "Estado publicador: desconocido";
  }
  if (state.presenceRefreshTimer) {
    clearInterval(state.presenceRefreshTimer);
    state.presenceRefreshTimer = null;
  }
  renderPresence([]);
  renderTypingIndicator();
  loginForm.reset();
  setPanels(false);
}

function startPresencePolling() {
  if (state.presenceRefreshTimer) {
    clearInterval(state.presenceRefreshTimer);
  }
  // Presence is now pushed via WebSocket /topic/presence subscription
  // This function is kept for backward compatibility but no longer polls
}

function renderPresence(users) {
  if (!presenceCountEl || !presenceListEl) {
    return;
  }

  const normalized = (users || [])
    .filter((user) => typeof user === "string" && user.trim().length > 0)
    .map((user) => user.trim());

  const uniqueSortedUsers = [...new Set(normalized)].sort((a, b) => a.localeCompare(b, "es", { sensitivity: "base" }));
  presenceCountEl.textContent = `${uniqueSortedUsers.length} usuarios`;
  presenceListEl.innerHTML = "";

  if (uniqueSortedUsers.length === 0) {
    const empty = document.createElement("li");
    empty.textContent = "Sin usuarios conectados";
    presenceListEl.appendChild(empty);
    return;
  }

  uniqueSortedUsers.forEach((user) => {
    const item = document.createElement("li");
    if (state.username && user === state.username && state.alias) {
      item.textContent = `${state.alias} (${state.phone || state.username})`;
    } else {
      item.textContent = user;
    }
    presenceListEl.appendChild(item);
  });
}

async function refreshPresence() {
  if (!state.token) {
    renderPresence([]);
    return;
  }

  try {
    const data = await api("/chat/presence", { method: "GET" });
    renderPresence(data.users || []);
  } catch {
    renderPresence([]);
  }
}

function setSimulationStatusText(running) {
  if (!simulationStatusEl) {
    return;
  }
  if (running === true) {
    simulationStatusEl.textContent = "Estado publicador: activo";
    return;
  }
  if (running === false) {
    simulationStatusEl.textContent = "Estado publicador: caido (cola acumulando mensajes)";
    return;
  }
  simulationStatusEl.textContent = "Estado publicador: desconocido";
}

async function refreshSimulationStatus() {
  if (!state.token) {
    setSimulationStatusText(undefined);
    return;
  }

  try {
    const data = await api("/chat/simulation/realtime-publisher/status", { method: "GET" });
    setSimulationStatusText(Boolean(data.running));
  } catch {
    setSimulationStatusText(undefined);
  }
}

async function simulatePublisherFailure() {
  try {
    await api("/chat/simulation/realtime-publisher/fail", { method: "POST" });
    setSimulationStatusText(false);
  } catch {
    setSimulationStatusText(undefined);
  }
}

async function simulatePublisherRestore() {
  try {
    await api("/chat/simulation/realtime-publisher/restore", { method: "POST" });
    setSimulationStatusText(true);
  } catch {
    setSimulationStatusText(undefined);
  }
}

function setSocketStatus(online) {
  socketDotEl.classList.toggle("online", online);
  socketDotEl.classList.toggle("offline", !online);
  socketTextEl.textContent = online ? "conectado" : "desconectado";
}

function appendMessage(message, isOptimistic = false) {
  const item = document.createElement("div");
  item.className = "msg" + (isOptimistic ? " msg-optimistic" : "");
  if (message.optimisticId) {
    item.id = `msg-${message.optimisticId}`;
  }

  const meta = document.createElement("div");
  meta.className = "meta";
  const created = message.createdAt ? new Date(message.createdAt).toLocaleTimeString() : "";
  const status = isOptimistic ? " (enviando...)" : "";
  // Prefer alias display when message originates from current user (backend uses phone as username)
  let displayedSender = message.sender || "system";
  if (message.sender && state.username && message.sender === state.username && state.alias) {
    displayedSender = `${state.alias} (${state.phone || state.username})`;
  }
  meta.textContent = `${displayedSender} ${created}${status}`.trim();

  const content = document.createElement("div");
  content.textContent = message.content || "";

  item.append(meta, content);
  messagesEl.appendChild(item);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function scrollMessagesToBottom() {
  requestAnimationFrame(() => {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  });
}

function renderTypingIndicator() {
  const users = [...state.remoteTypingUsers].filter((username) => username && username !== state.username);
  if (users.length === 0) {
    typingIndicatorEl.classList.add("hidden");
    typingIndicatorEl.textContent = "";
    return;
  }

  typingIndicatorEl.classList.remove("hidden");
  typingIndicatorEl.textContent = users.length === 1
    ? `${users[0]} esta escribiendo...`
    : `${users.length} personas estan escribiendo...`;
}

function clearRemoteTypingState() {
  state.remoteTypingTimers.forEach((timerId) => {
    clearTimeout(timerId);
  });
  state.remoteTypingTimers.clear();
  state.remoteTypingUsers.clear();
  renderTypingIndicator();
}

function publishTyping(typing) {
  if (!state.stompClient || !state.stompClient.connected || !state.username) {
    return;
  }

  state.stompClient.publish({
    destination: "/app/typing",
    body: JSON.stringify({
      conversationId: state.conversationId,
      username: state.username,
      typing,
    }),
  });
}

function sendTypingEvent(typing) {
  if (typing === state.isTyping) {
    return;
  }

  state.isTyping = typing;
  publishTyping(typing);
}

function scheduleStopTyping() {
  if (state.typingStopTimer) {
    clearTimeout(state.typingStopTimer);
  }
  state.typingStopTimer = setTimeout(() => {
    sendTypingEvent(false);
  }, TYPING_STOP_DELAY_MS);
}

function handleTypingEvent(event) {
  if (!event || event.conversationId !== state.conversationId || !event.username || event.username === state.username) {
    return;
  }

  if (!event.typing) {
    const existingTimer = state.remoteTypingTimers.get(event.username);
    if (existingTimer) {
      clearTimeout(existingTimer);
      state.remoteTypingTimers.delete(event.username);
    }
    state.remoteTypingUsers.delete(event.username);
    renderTypingIndicator();
    return;
  }

  state.remoteTypingUsers.add(event.username);
  const existingTimer = state.remoteTypingTimers.get(event.username);
  if (existingTimer) {
    clearTimeout(existingTimer);
  }
  const timerId = setTimeout(() => {
    state.remoteTypingUsers.delete(event.username);
    state.remoteTypingTimers.delete(event.username);
    renderTypingIndicator();
  }, TYPING_STOP_DELAY_MS + 800);

  state.remoteTypingTimers.set(event.username, timerId);
  renderTypingIndicator();
}

async function api(url, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
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

async function ensureConversation() {
  try {
    const response = await api("/chat/conversations", {
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
  if (state.isLoadingMessages || state.currentPage >= state.totalPages) {
    if (loadMoreBtn) {
      loadMoreBtn.classList.add("hidden");
    }
    return;
  }

  state.isLoadingMessages = true;
  if (loadMoreStatusEl) {
    loadMoreStatusEl.textContent = "Cargando...";
  }

  try {
    const data = await api(`/chat/conversations/${state.conversationId}/messages?page=${state.currentPage}&size=${PAGE_SIZE}`, {
      method: "GET",
    });

    state.totalPages = data.totalPages || 1;
    if (data.messages) {
      data.messages.forEach(msg => appendMessage(msg, false));
      state.currentPage++;
    }

    if (state.currentPage < state.totalPages && loadMoreBtn) {
      loadMoreBtn.classList.remove("hidden");
    } else if (loadMoreBtn) {
      loadMoreBtn.classList.add("hidden");
    }
  } catch (err) {
    if (loadMoreStatusEl) {
      loadMoreStatusEl.textContent = "Error al cargar mensajes";
    }
  } finally {
    state.isLoadingMessages = false;
    scrollMessagesToBottom();
  }
}

function connectWebSocket() {
  disconnectWebSocket();
  clearRemoteTypingState();

  const socket = new SockJS("/ws");
  const client = new StompJs.Client({
    webSocketFactory: () => socket,
    connectHeaders: {
      username: state.username,
      alias: state.alias || "",
      phone: state.phone || state.username
    },
    reconnectDelay: 5000,
    onConnect: () => {
      setSocketStatus(true);
      client.subscribe(`/topic/conversations/${state.conversationId}`, (frame) => {
        const event = JSON.parse(frame.body);
        appendMessage(event);
      });
      client.subscribe(`/topic/conversations/${state.conversationId}/typing`, (frame) => {
        const event = JSON.parse(frame.body);
        handleTypingEvent(event);
      });
      // Subscribe to presence updates (pushed by server)
      client.subscribe(`/topic/presence`, (frame) => {
        const snapshot = JSON.parse(frame.body);
        renderPresence(snapshot.users || []);
      });
      // Get initial presence snapshot
      refreshPresence();
      // Note: startPresencePolling() no longer polls; presence is now push-based
      startPresencePolling();
    },
    onStompError: () => setSocketStatus(false),
    onWebSocketClose: () => setSocketStatus(false),
  });

  state.stompClient = client;
  client.activate();
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  authErrorEl.textContent = "";

  const phone = document.getElementById("phone").value.trim();
  const alias = document.getElementById("alias").value.trim();
  const password = document.getElementById("password").value;

  try {
    // Send phone as the username identifier to the auth API and include alias
    const login = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: phone, password, alias }),
    });

    state.token = login.token;
    // keep phone as the canonical username used by backend
    state.username = phone;
    state.phone = phone;
    state.alias = alias || "";

    await ensureConversation();
    setSessionInfo();
    setPanels(true);
    await loadHistory();
    await refreshSimulationStatus();
    connectWebSocket();
    saveSession();
  } catch (error) {
    authErrorEl.textContent = "No fue posible iniciar sesion";
  }
});

messageForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const content = messageInputEl.value.trim();
  if (!content) {
    return;
  }

  const optimisticId = `temp-${Date.now()}`;
  const optimisticMessage = {
    optimisticId,
    sender: state.alias || state.phone || state.username,
    content,
    createdAt: new Date().toISOString(),
  };

  // Show optimistic message immediately
  appendMessage(optimisticMessage, true);
  state.optimisticMessages.set(optimisticId, optimisticMessage);
  messageInputEl.value = "";

  try {
    sendTypingEvent(false);
    await api("/chat/messages", {
      method: "POST",
      body: JSON.stringify({
        conversationId: state.conversationId,
        content,
      }),
    });
    // Remove optimistic state on success
    state.optimisticMessages.delete(optimisticId);
    const msgEl = document.getElementById(`msg-${optimisticId}`);
    if (msgEl) {
      msgEl.classList.remove("msg-optimistic");
    }
  } catch (err) {
    // Update optimistic message to show error state
    const msgEl = document.getElementById(`msg-${optimisticId}`);
    if (msgEl) {
      msgEl.classList.add("msg-error");
      msgEl.title = "Falló al enviar. Intenta de nuevo.";
    }
    // Keep in optimisticMessages for retry
  }
});

messageInputEl.addEventListener("input", () => {
  const hasContent = messageInputEl.value.trim().length > 0;
  if (!hasContent) {
    sendTypingEvent(false);
    return;
  }

  sendTypingEvent(true);
  scheduleStopTyping();
});

if (logoutBtn) {
  logoutBtn.addEventListener("click", () => {
    logout();
  });
}

if (simulateFailBtn) {
  simulateFailBtn.addEventListener("click", async () => {
    await simulatePublisherFailure();
  });
}

if (simulateRestoreBtn) {
  simulateRestoreBtn.addEventListener("click", async () => {
    await simulatePublisherRestore();
  });
}

if (loadMoreBtn) {
  loadMoreBtn.addEventListener("click", async () => {
    await loadMoreMessages();
  });
}

async function restoreSession() {
  const saved = localStorage.getItem(SESSION_STORAGE_KEY);
  if (!saved) {
    return;
  }

  try {
    const parsed = JSON.parse(saved);
    if (!parsed?.token || !parsed?.username) {
      clearSession();
      return;
    }

    state.token = parsed.token;
    state.username = parsed.username;
    state.alias = parsed.alias || "";
    state.phone = parsed.phone || parsed.username;
    if (Number.isInteger(parsed.conversationId) && parsed.conversationId > 0) {
      state.conversationId = parsed.conversationId;
    }

    await api("/auth/validate", { method: "GET" });
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

setSocketStatus(false);
setPanels(false);
renderPresence([]);
renderTypingIndicator();
restoreSession();
