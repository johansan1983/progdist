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

const SESSION_STORAGE_KEY = "superchat.session";
const TYPING_STOP_DELAY_MS = 1400;

const state = {
  token: "",
  username: "",
  conversationId: 1,
  stompClient: null,
  presenceRefreshTimer: null,
  isTyping: false,
  typingStopTimer: null,
  remoteTypingTimers: new Map(),
  remoteTypingUsers: new Set(),
};

function setPanels(isAuthenticated) {
  authPanel.classList.toggle("hidden", isAuthenticated);
  chatPanel.classList.toggle("hidden", !isAuthenticated);
}

function setSessionInfo() {
  sessionInfoEl.textContent = `Usuario: ${state.username} | Conversacion: ${state.conversationId}`;
}

function saveSession() {
  const payload = {
    token: state.token,
    username: state.username,
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
  state.conversationId = 1;
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
  state.presenceRefreshTimer = setInterval(() => {
    refreshPresence();
  }, 3000);
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
    item.textContent = user;
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

function appendMessage(message) {
  const item = document.createElement("div");
  item.className = "msg";

  const meta = document.createElement("div");
  meta.className = "meta";
  const created = message.createdAt ? new Date(message.createdAt).toLocaleTimeString() : "";
  meta.textContent = `${message.sender || "system"} ${created}`.trim();

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
  const data = await api(`/chat/conversations/${state.conversationId}/messages`, {
    method: "GET",
  });
  data.forEach(appendMessage);
  scrollMessagesToBottom();
}

function connectWebSocket() {
  disconnectWebSocket();
  clearRemoteTypingState();

  const socket = new SockJS("/ws");
  const client = new StompJs.Client({
    webSocketFactory: () => socket,
    connectHeaders: {
      username: state.username,
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
      refreshPresence();
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

  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value;

  try {
    const login = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });

    state.token = login.token;
    state.username = login.username;

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

  try {
    sendTypingEvent(false);
    await api("/chat/messages", {
      method: "POST",
      body: JSON.stringify({
        conversationId: state.conversationId,
        content,
      }),
    });
    messageInputEl.value = "";
  } catch {
    appendMessage({
      sender: "system",
      content: "No se pudo enviar el mensaje",
      createdAt: new Date().toISOString(),
    });
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
