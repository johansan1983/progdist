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
const presenceCountEl = document.getElementById("presenceCount");
const presenceListEl = document.getElementById("presenceList");
const loadMoreBtn = document.getElementById("loadMoreBtn");
const loadMoreStatusEl = document.getElementById("loadMoreStatus");

const SESSION_STORAGE_KEY = "superchat.session";
const TYPING_STOP_DELAY_MS = 1400;
const PAGE_SIZE = 50;
const KEYCLOAK_TOKEN_URL = "/kc/realms/superchat/protocol/openid-connect/token";
const KEYCLOAK_CLIENT_ID = "superchat-frontend";

// ── i18n (ES / EN / FR) ───────────────────────────────────────────────────────
const LANG_STORAGE_KEY = "superchat.lang";
const SUPPORTED_LANGS = ["es", "en", "fr"];

const I18N = {
  es: {
    appSubtitle: "Plataforma corporativa de mensajería",
    user: "Usuario", password: "Contraseña", login: "Entrar",
    logout: "Cerrar sesión", connected: "conectado", disconnected: "desconectado",
    conversations: "Conversaciones", online: "Conectados", newDm: "+ DM",
    loadMore: "Cargar más mensajes", composerPlaceholder: "Escribe un mensaje...",
    send: "Enviar", sending: "enviando…",
    attach: "Adjuntar archivo", emoji: "Emojis", viewOnce: "Ver una vez",
    voice: "Mensaje de voz", chatGeneral: "Chat General",
    sessionInfo: "Usuario: {user} | Conversación: {conv}",
    usersCount: "{n} usuarios", noUsersOnline: "Sin usuarios conectados",
    loading: "Cargando…", errorLoadingMessages: "Error al cargar mensajes",
    loginFailed: "No fue posible iniciar sesión. Verifica usuario y contraseña.",
    downloadFile: "Descargar archivo", disappearsIn: "Desaparecerá en {s}s",
    oneTyping: "{name} está escribiendo…", manyTyping: "{n} personas están escribiendo…",
    noMessages: "No hay mensajes aún", sayHi: "Sé el primero en saludar 👋",
    noConversations: "No hay conversaciones",
    today: "Hoy", yesterday: "Ayer",
    sendFailed: "Falló al enviar. Intenta de nuevo.",
    uploadError: "Error al subir el archivo: ", attachError: "Error al preparar el adjunto: ",
    audioError: "Error al preparar el audio: ", dmError: "Error al crear DM: ",
    noAudioSupport: "Tu navegador no soporta grabación de audio.",
    noMic: "No se encontró ningún micrófono conectado.",
    recordTooBig: "La grabación supera 50 MB.", stopRecording: "Detener grabación",
    fileTooBig: "El archivo no puede superar 50 MB.",
    micBlocked: "El micrófono está bloqueado. Haz clic en el ícono 🔒 de la barra de direcciones, permite el acceso y recarga la página.",
    micDenied: "Permiso de micrófono denegado. Haz clic en el ícono 🔒 de la barra de direcciones y permite el acceso.",
    micError: "No se pudo acceder al micrófono: ",
    newDmTitle: "Nuevo mensaje directo", searchUser: "Buscar usuario…", cancel: "Cancelar",
    consentTitle: "Consentimiento de tratamiento de datos",
    consentIntro: "Tu organización requiere que aceptes su política de tratamiento de datos antes de usar SuperChat.",
    consentP1: "Tus mensajes se almacenan cifrados y están sujetos a la política de retención de tu organización.",
    consentP2: "Los administradores pueden revisar mensajes con fines de cumplimiento.",
    consentP3: "Puedes solicitar una copia o la eliminación de tus datos en cualquier momento.",
    consentVersionLabel: "Versión de la política:", consentAccept: "Acepto",
    consentDecline: "Rechazar y salir",
    noticeWorkingHours: "⏰ El envío de mensajes fuera del horario laboral está deshabilitado por tu organización.",
    noticeDmDisabled: "🚫 Los mensajes directos están deshabilitados por tu organización.",
    noticeBlocked: "⚠️ Tu mensaje fue bloqueado por la política de contenido.",
  },
  en: {
    appSubtitle: "Corporate messaging platform",
    user: "Username", password: "Password", login: "Sign in",
    logout: "Sign out", connected: "connected", disconnected: "disconnected",
    conversations: "Conversations", online: "Online", newDm: "+ DM",
    loadMore: "Load more messages", composerPlaceholder: "Type a message…",
    send: "Send", sending: "sending…",
    attach: "Attach file", emoji: "Emojis", viewOnce: "View once",
    voice: "Voice message", chatGeneral: "General Chat",
    sessionInfo: "User: {user} | Conversation: {conv}",
    usersCount: "{n} users", noUsersOnline: "No users online",
    loading: "Loading…", errorLoadingMessages: "Error loading messages",
    loginFailed: "Sign-in failed. Check your username and password.",
    downloadFile: "Download file", disappearsIn: "Disappears in {s}s",
    oneTyping: "{name} is typing…", manyTyping: "{n} people are typing…",
    noMessages: "No messages yet", sayHi: "Be the first to say hi 👋",
    noConversations: "No conversations",
    today: "Today", yesterday: "Yesterday",
    sendFailed: "Failed to send. Try again.",
    uploadError: "File upload error: ", attachError: "Could not prepare attachment: ",
    audioError: "Could not prepare audio: ", dmError: "Could not create DM: ",
    noAudioSupport: "Your browser does not support audio recording.",
    noMic: "No microphone found.",
    recordTooBig: "Recording exceeds 50 MB.", stopRecording: "Stop recording",
    fileTooBig: "File cannot exceed 50 MB.",
    micBlocked: "The microphone is blocked. Click the 🔒 icon in the address bar, allow access, then reload the page.",
    micDenied: "Microphone permission denied. Click the 🔒 icon in the address bar and allow access.",
    micError: "Could not access the microphone: ",
    newDmTitle: "New direct message", searchUser: "Search user…", cancel: "Cancel",
    consentTitle: "Data processing consent",
    consentIntro: "Your organization requires you to accept its data processing policy before you can use SuperChat.",
    consentP1: "Your messages are stored encrypted and subject to your organization's retention policy.",
    consentP2: "Administrators can review messages for compliance purposes.",
    consentP3: "You may request a copy or deletion of your data at any time.",
    consentVersionLabel: "Policy version:", consentAccept: "I accept",
    consentDecline: "Decline and sign out",
    noticeWorkingHours: "⏰ Messaging outside working hours is disabled by your organization.",
    noticeDmDisabled: "🚫 Direct messaging is disabled by your organization.",
    noticeBlocked: "⚠️ Your message was blocked by the content policy.",
  },
  fr: {
    appSubtitle: "Plateforme de messagerie d'entreprise",
    user: "Utilisateur", password: "Mot de passe", login: "Se connecter",
    logout: "Se déconnecter", connected: "connecté", disconnected: "déconnecté",
    conversations: "Conversations", online: "En ligne", newDm: "+ MP",
    loadMore: "Charger plus de messages", composerPlaceholder: "Écrivez un message…",
    send: "Envoyer", sending: "envoi…",
    attach: "Joindre un fichier", emoji: "Émojis", viewOnce: "Vue unique",
    voice: "Message vocal", chatGeneral: "Discussion générale",
    sessionInfo: "Utilisateur : {user} | Conversation : {conv}",
    usersCount: "{n} utilisateurs", noUsersOnline: "Aucun utilisateur en ligne",
    loading: "Chargement…", errorLoadingMessages: "Erreur de chargement des messages",
    loginFailed: "Échec de la connexion. Vérifiez votre identifiant et votre mot de passe.",
    downloadFile: "Télécharger le fichier", disappearsIn: "Disparaît dans {s}s",
    oneTyping: "{name} est en train d'écrire…", manyTyping: "{n} personnes écrivent…",
    noMessages: "Aucun message", sayHi: "Soyez le premier à dire bonjour 👋",
    noConversations: "Aucune conversation",
    today: "Aujourd'hui", yesterday: "Hier",
    sendFailed: "Échec de l'envoi. Réessayez.",
    uploadError: "Erreur de téléversement : ", attachError: "Impossible de préparer la pièce jointe : ",
    audioError: "Impossible de préparer l'audio : ", dmError: "Impossible de créer le MP : ",
    noAudioSupport: "Votre navigateur ne prend pas en charge l'enregistrement audio.",
    noMic: "Aucun microphone détecté.",
    recordTooBig: "L'enregistrement dépasse 50 Mo.", stopRecording: "Arrêter l'enregistrement",
    fileTooBig: "Le fichier ne peut pas dépasser 50 Mo.",
    micBlocked: "Le microphone est bloqué. Cliquez sur l'icône 🔒 dans la barre d'adresse, autorisez l'accès, puis rechargez la page.",
    micDenied: "Autorisation du microphone refusée. Cliquez sur l'icône 🔒 dans la barre d'adresse et autorisez l'accès.",
    micError: "Impossible d'accéder au microphone : ",
    newDmTitle: "Nouveau message direct", searchUser: "Rechercher un utilisateur…", cancel: "Annuler",
    consentTitle: "Consentement au traitement des données",
    consentIntro: "Votre organisation exige que vous acceptiez sa politique de traitement des données avant d'utiliser SuperChat.",
    consentP1: "Vos messages sont stockés chiffrés et soumis à la politique de conservation de votre organisation.",
    consentP2: "Les administrateurs peuvent consulter les messages à des fins de conformité.",
    consentP3: "Vous pouvez demander une copie ou la suppression de vos données à tout moment.",
    consentVersionLabel: "Version de la politique :", consentAccept: "J'accepte",
    consentDecline: "Refuser et se déconnecter",
    noticeWorkingHours: "⏰ L'envoi de messages en dehors des heures de travail est désactivé par votre organisation.",
    noticeDmDisabled: "🚫 Les messages directs sont désactivés par votre organisation.",
    noticeBlocked: "⚠️ Votre message a été bloqué par la politique de contenu.",
  },
};

function detectLang() {
  const saved = localStorage.getItem(LANG_STORAGE_KEY);
  if (saved && SUPPORTED_LANGS.includes(saved)) return saved;
  const nav = (navigator.language || "es").slice(0, 2).toLowerCase();
  return SUPPORTED_LANGS.includes(nav) ? nav : "es";
}

let LANG = detectLang();

function t(key, vars) {
  let s = (I18N[LANG] && I18N[LANG][key]) || I18N.es[key] || key;
  if (vars) for (const k in vars) s = s.replace(`{${k}}`, vars[k]);
  return s;
}

function applyTranslations() {
  document.documentElement.lang = LANG;
  document.querySelectorAll("[data-i18n]").forEach(el => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll("[data-i18n-ph]").forEach(el => { el.placeholder = t(el.dataset.i18nPh); });
  document.querySelectorAll("[data-i18n-aria]").forEach(el => {
    el.setAttribute("aria-label", t(el.dataset.i18nAria));
    el.setAttribute("title", t(el.dataset.i18nAria));
  });
  document.querySelectorAll(".lang-switcher select").forEach(sel => { sel.value = LANG; });
}

function setLang(lang) {
  if (!SUPPORTED_LANGS.includes(lang)) return;
  LANG = lang;
  localStorage.setItem(LANG_STORAGE_KEY, lang);
  applyTranslations();
  // Re-render dynamic UI that isn't driven by data-i18n
  setSocketStatus(socketDotEl.classList.contains("online"));
  renderTypingIndicator();
  if (state.token) setSessionInfo();
}

const state = {
  token: "",
  refreshToken: "",
  tokenExpiresAt: 0,
  username: "",
  displayName: "",
  orgId: "",
  systemRole: "",
  consentChecked: false,
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
  bgSubs: new Map(),       // convId → stomp subscription for background unread tracking
  unreadCounts: new Map(), // convId → unread count
  pendingAttachment: null, // { file, uploadUrl, publicUrl, attachmentType }
  presignInProgress: false,
  viewOnce: false,
  lastRenderedDay: null,   // tracks day-of last rendered message for date dividers
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
    orgId: state.orgId,
    systemRole: state.systemRole,
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
  sessionInfoEl.textContent = t("sessionInfo", { user: display, conv: state.conversationId });
}

function setSocketStatus(online) {
  socketDotEl.classList.toggle("online", online);
  socketDotEl.classList.toggle("offline", !online);
  socketTextEl.textContent = online ? t("connected") : t("disconnected");
}

function disconnectWebSocket() {
  state.bgSubs.forEach(sub => sub.unsubscribe());
  state.bgSubs.clear();
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
  state.orgId = "";
  state.systemRole = "";
  state.consentChecked = false;
  state.conversationId = 1;
  state.currentPage = 0;
  state.totalPages = 0;
  state.optimisticMessages.clear();
  state.unreadCounts.clear();
  clearInterval(state.presenceInterval);
  state.presenceInterval = null;
}

function logout() {
  sendTypingEvent(false);
  stopRecording();
  disconnectWebSocket();
  clearSession();
  resetState();
  messagesEl.innerHTML = "";
  sessionInfoEl.textContent = "";
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
  if (state.orgId) headers["X-Org-Id"] = state.orgId;
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

// ── User profile + org context ────────────────────────────────────────────────

async function fetchUserProfile() {
  try {
    const profile = await api("/api/users/me");
    if (profile.orgId) state.orgId = profile.orgId;
    if (profile.systemRole) state.systemRole = profile.systemRole;
    if (profile.displayName) state.displayName = profile.displayName;
  } catch {
    // fail open — org context is optional
  }
}

// ── Consent gate ──────────────────────────────────────────────────────────────

async function checkConsent() {
  if (!state.orgId || state.consentChecked) return true;
  try {
    const status = await api(`/api/compliance/consent/${state.orgId}/status`);
    if (status.active) {
      state.consentChecked = true;
      return true;
    }
    // Need consent — fetch org rules to get current version
    let version = 1;
    try {
      const rules = await api(`/api/admin/organizations/${state.orgId}/rules`);
      const versionRule = rules.find(r => r.key === "consent_version");
      if (versionRule) version = parseInt(versionRule.value, 10) || 1;
    } catch { /* use version 1 */ }

    return await showConsentModal(version);
  } catch {
    return true; // fail open if compliance service unavailable
  }
}

function showConsentModal(version) {
  return new Promise(resolve => {
    const modal = document.getElementById("consentModal");
    if (!modal) { resolve(true); return; }
    document.getElementById("consentVersion").textContent = version;
    modal.classList.remove("hidden");

    const acceptBtn = document.getElementById("consentAcceptBtn");
    const declineBtn = document.getElementById("consentDeclineBtn");

    async function onAccept() {
      acceptBtn.removeEventListener("click", onAccept);
      declineBtn.removeEventListener("click", onDecline);
      try {
        await api("/api/compliance/consent", {
          method: "POST",
          body: JSON.stringify({ orgId: state.orgId, version }),
        });
        state.consentChecked = true;
        modal.classList.add("hidden");
        resolve(true);
      } catch {
        modal.classList.add("hidden");
        resolve(false);
      }
    }

    function onDecline() {
      acceptBtn.removeEventListener("click", onAccept);
      declineBtn.removeEventListener("click", onDecline);
      modal.classList.add("hidden");
      logout();
      resolve(false);
    }

    acceptBtn.addEventListener("click", onAccept);
    declineBtn.addEventListener("click", onDecline);
  });
}

// ── Channel type ──────────────────────────────────────────────────────────────

function channelIcon(channelType) {
  switch (channelType) {
    case 'ANNOUNCEMENT': return '📢';
    case 'TEAM':         return '👥';
    case 'CLASS':        return '🎓';
    case 'SUPPORT':      return '🆘';
    default:             return '#';
  }
}

// ── Business rules notice ─────────────────────────────────────────────────────

function showRulesNotice(text, durationMs = 6000) {
  const notice = document.getElementById("rulesNotice");
  const noticeText = document.getElementById("rulesNoticeText");
  const dismiss = document.getElementById("rulesNoticeDismiss");
  if (!notice || !noticeText) return;
  noticeText.textContent = text;
  notice.classList.remove("hidden");
  const hide = () => notice.classList.add("hidden");
  dismiss?.addEventListener("click", hide, { once: true });
  setTimeout(hide, durationMs);
}

// ── Presence ──────────────────────────────────────────────────────────────────

function renderPresence(users) {
  if (!presenceCountEl || !presenceListEl) return;

  const normalized = (users || [])
    .filter(u => typeof u === "string" && u.trim().length > 0)
    .map(u => u.trim());
  const unique = [...new Set(normalized)].sort((a, b) => a.localeCompare(b, "es", { sensitivity: "base" }));

  presenceCountEl.textContent = t("usersCount", { n: unique.length });
  presenceListEl.innerHTML = "";

  if (unique.length === 0) {
    const empty = document.createElement("li");
    empty.textContent = t("noUsersOnline");
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

// ── Messages ──────────────────────────────────────────────────────────────────

function resolveDisplayName(message) {
  if (message.senderName) return message.senderName;
  const sender = message.sender || "system";
  return (sender === state.username && state.displayName) ? state.displayName : sender;
}

function dayKey(iso) {
  const d = iso ? new Date(iso) : new Date();
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

function dividerLabel(iso) {
  const d = iso ? new Date(iso) : new Date();
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (dayKey(iso) === dayKey(today)) return t("today");
  if (dayKey(iso) === dayKey(yesterday)) return t("yesterday");
  return d.toLocaleDateString(LANG, { day: "numeric", month: "long", year: "numeric" });
}

function maybeInsertDateDivider(iso) {
  const key = dayKey(iso);
  if (state.lastRenderedDay === key) return;
  state.lastRenderedDay = key;
  const divider = document.createElement("div");
  divider.className = "date-divider";
  divider.textContent = dividerLabel(iso);
  messagesEl.appendChild(divider);
}

function appendMessage(message, isOptimistic = false) {
  // Deduplicate: match incoming real message against a pending optimistic one by content.
  // Check the Map first (populated before the REST call) so we catch the race where the
  // WebSocket echo arrives before the REST response sets data-confirmed on the DOM element.
  if (!isOptimistic && message.sender === state.username) {
    const msgContent = message.content || '';
    for (const [id, pendingContent] of state.optimisticMessages) {
      if (pendingContent === msgContent) {
        state.optimisticMessages.delete(id);
        const el = document.getElementById(`msg-${id}`);
        if (el) {
          el.classList.remove('msg-optimistic');
          const metaEl = el.querySelector('.meta');
          if (metaEl) metaEl.textContent = metaEl.textContent.replace(/\s*\([^)]*\)\s*$/, '');
        }
        return;
      }
    }
    // Fallback: WebSocket echo arrived after REST response already confirmed the element
    const confirmed = messagesEl.querySelector('[data-confirmed]');
    if (confirmed && confirmed.dataset.content === msgContent) {
      confirmed.removeAttribute('data-confirmed');
      confirmed.removeAttribute('data-content');
      return;
    }
  }

  const isOwn = message.sender === state.username;
  const item = document.createElement("div");
  item.className = "msg" + (isOwn ? " msg-own" : "") + (isOptimistic ? " msg-optimistic" : "");
  if (message.optimisticId) item.id = `msg-${message.optimisticId}`;

  const meta = document.createElement("div");
  meta.className = "meta";
  const created = message.createdAt ? new Date(message.createdAt).toLocaleTimeString(LANG, { hour: "2-digit", minute: "2-digit" }) : "";
  const status = isOptimistic ? ` (${t("sending")})` : "";
  meta.textContent = `${resolveDisplayName(message)} ${created}${status}`.trim();

  // View-once expired — skip entirely (message was already seen by recipient)
  if (message.viewOnceExpired) return;

  const content = document.createElement("div");
  content.textContent = message.content || "";

  // View-once badge
  if (message.viewOnce) {
    const badge = document.createElement("span");
    badge.className = "view-once-badge";
    badge.textContent = "🔥";
    content.appendChild(badge);
  }

  item.append(meta, content);

  // Remove the empty-state placeholder once a real message arrives
  const emptyEl = messagesEl.querySelector('.empty-state');
  if (emptyEl) emptyEl.remove();

  // Insert a date divider when the calendar day changes
  maybeInsertDateDivider(message.createdAt);

  if (message.attachmentType === 'IMAGE' && message.attachmentUrl) {
    const img = document.createElement('img');
    img.src = message.attachmentUrl;
    img.className = 'msg-image';
    img.loading = 'lazy';
    img.addEventListener('click', () => openLightbox(message.attachmentUrl));
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
    link.textContent = `📎 ${t('downloadFile')}`;
    link.className = 'msg-file';
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    item.appendChild(link);
  }

  messagesEl.appendChild(item);
  messagesEl.scrollTop = messagesEl.scrollHeight;

  // Auto-remove view-once messages for the recipient after 5 seconds
  if (message.viewOnce && message.sender !== state.username && !isOptimistic) {
    const countdown = document.createElement('div');
    countdown.className = 'view-once-countdown';
    let secs = 5;
    countdown.textContent = `🔥 ${t('disappearsIn', { s: secs })}`;
    item.appendChild(countdown);

    const timer = setInterval(() => {
      secs--;
      if (secs <= 0) {
        clearInterval(timer);
        item.style.transition = 'opacity 0.6s';
        item.style.opacity = '0';
        setTimeout(() => item.remove(), 600);
      } else {
        countdown.textContent = `🔥 ${t('disappearsIn', { s: secs })}`;
      }
    }, 1000);
  }
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
  state.lastRenderedDay = null;
  state.optimisticMessages.clear();
  await loadMoreMessages();
  renderMessagesEmptyStateIfNeeded();
}

function renderMessagesEmptyStateIfNeeded() {
  if (messagesEl.querySelector(".msg") || messagesEl.querySelector(".empty-state")) return;
  const empty = document.createElement("div");
  empty.className = "empty-state";
  empty.innerHTML = `<span class="empty-emoji">💬</span>${t("noMessages")}<br>${t("sayHi")}`;
  messagesEl.appendChild(empty);
}

async function loadMoreMessages() {
  if (state.isLoadingMessages || state.currentPage >= state.totalPages && state.totalPages > 0) {
    if (loadMoreBtn) loadMoreBtn.classList.add("hidden");
    return;
  }

  state.isLoadingMessages = true;
  if (loadMoreStatusEl) loadMoreStatusEl.textContent = t("loading");

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
    if (loadMoreStatusEl) loadMoreStatusEl.textContent = t("errorLoadingMessages");
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
    subscribeBackgroundConversations();
  } catch (e) {
    console.warn('Could not load conversations', e);
  }
}

function subscribeBackgroundConversations() {
  if (!state.stompClient?.connected) return;
  const activeId = state.activeConversationId ?? state.conversationId;
  state.conversations.forEach(conv => {
    if (conv.id === activeId) return; // active conv handled by msgSub
    if (state.bgSubs.has(conv.id)) return; // already subscribed
    const sub = state.stompClient.subscribe(`/topic/conversations.${conv.id}`, () => {
      const count = (state.unreadCounts.get(conv.id) || 0) + 1;
      state.unreadCounts.set(conv.id, count);
      renderConvList();
    });
    state.bgSubs.set(conv.id, sub);
  });
}

function renderConvList() {
  const ul = document.getElementById('convList');
  if (!ul) return;
  ul.innerHTML = '';
  if (!state.conversations.length) {
    const li = document.createElement('li');
    li.className = 'conv-empty';
    li.textContent = t('noConversations');
    ul.appendChild(li);
    return;
  }
  const activeId = state.activeConversationId ?? state.conversationId;
  state.conversations.forEach(conv => {
    const li = document.createElement('li');
    const label = conv.type === 'DIRECT'
      ? `@ ${conv.otherParticipantName || conv.id}`
      : `${channelIcon(conv.channelType)} ${conv.name || conv.id}`;
    li.title = label;
    li.dataset.id = String(conv.id);
    if (conv.id === activeId) li.classList.add('active');

    const labelSpan = document.createElement('span');
    labelSpan.textContent = label;
    labelSpan.style.overflow = 'hidden';
    labelSpan.style.textOverflow = 'ellipsis';
    li.appendChild(labelSpan);

    const unread = state.unreadCounts.get(conv.id) || 0;
    if (unread > 0) {
      const badge = document.createElement('span');
      badge.className = 'unread-badge';
      badge.textContent = unread > 99 ? '99+' : String(unread);
      li.appendChild(badge);
    }

    li.addEventListener('click', () => switchConversation(conv.id, label));
    ul.appendChild(li);
  });
}

async function switchConversation(id, label) {
  const prevId = state.activeConversationId;
  state.activeConversationId = id;
  state.unreadCounts.delete(id);

  if (state.stompClient?.connected) {
    // Put old active conv into background tracking before switching
    if (prevId && prevId !== id && !state.bgSubs.has(prevId)) {
      const bg = state.stompClient.subscribe(`/topic/conversations.${prevId}`, () => {
        const count = (state.unreadCounts.get(prevId) || 0) + 1;
        state.unreadCounts.set(prevId, count);
        renderConvList();
      });
      state.bgSubs.set(prevId, bg);
    }
    subscribeToConversation(id);
  }
  const headerEl = document.getElementById('chatTitle');
  if (headerEl) headerEl.textContent = label || `Conversación #${id}`;
  const messagesEl = document.getElementById('messages');
  if (messagesEl) messagesEl.innerHTML = '';
  state.currentPage = 0;
  state.totalPages = 0;
  state.optimisticMessages.clear();
  closeMobileSidebar();
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
    ? t("oneTyping", { name: users[0] })
    : t("manyTyping", { n: users.length });
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
  // Drop any background sub for this conv — msgSub takes over
  const existing = state.bgSubs.get(conversationId);
  if (existing) { existing.unsubscribe(); state.bgSubs.delete(conversationId); }

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
      subscribeBackgroundConversations();
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

    await fetchUserProfile();
    const consentOk = await checkConsent();
    if (!consentOk) return;

    await ensureConversation();
    setSessionInfo();
    setPanels(true);
    await loadHistory();
    await loadConversations();
    connectWebSocket();
    saveSession();
  } catch {
    authErrorEl.textContent = t("loginFailed");
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
      alert(t("uploadError") + e.message);
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
  state.optimisticMessages.set(optimisticId, content || '');
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
    const saved = await api("/api/chat/messages", {
      method: "POST",
      body: JSON.stringify({ conversationId: state.activeConversationId ?? state.conversationId, content, attachmentUrl, attachmentType, viewOnce }),
    });
    // Moderation may rewrite the content server-side (e.g. REPLACE perro->gato). Reconcile the
    // optimistic bubble against the SAVED content, otherwise the WebSocket echo of the sanitized
    // text won't match our dedup key and renders as a duplicate.
    const savedContent = (saved && typeof saved.content === "string") ? saved.content : content;
    state.optimisticMessages.delete(optimisticId);
    const confirmedEl = document.getElementById(`msg-${optimisticId}`);
    if (confirmedEl) {
      confirmedEl.classList.remove("msg-optimistic");
      confirmedEl.dataset.confirmed = "true";
      confirmedEl.dataset.content = savedContent;
      // Reflect a moderation rewrite in the sender's own bubble (preserve any badge child).
      if (savedContent !== content) {
        const bodyDiv = confirmedEl.children[1];
        if (bodyDiv && bodyDiv.firstChild && bodyDiv.firstChild.nodeType === Node.TEXT_NODE) {
          bodyDiv.firstChild.nodeValue = savedContent;
        }
      }
      const metaEl = confirmedEl.querySelector(".meta");
      if (metaEl) metaEl.textContent = metaEl.textContent.replace(/\s*\([^)]*\)\s*$/, "");
    }
  } catch (err) {
    const el = document.getElementById(`msg-${optimisticId}`);
    if (el) { el.classList.add("msg-error"); el.title = t("sendFailed"); }
    const msg = err?.message || "";
    if (msg.includes("working hours")) {
      showRulesNotice(t("noticeWorkingHours"));
    } else if (msg.includes("Direct messaging is disabled")) {
      showRulesNotice(t("noticeDmDisabled"));
    } else if (msg.includes("content policy")) {
      showRulesNotice(t("noticeBlocked"));
    }
  }
});

messageInputEl.addEventListener("input", () => {
  const hasContent = messageInputEl.value.trim().length > 0;
  if (!hasContent) { sendTypingEvent(false); return; }
  sendTypingEvent(true);
  scheduleStopTyping();
});

logoutBtn?.addEventListener("click", logout);
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
    alert(t("fileTooBig"));
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
    const presignHeaders = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${state.token}`,
    };
    if (state.orgId) presignHeaders['X-Org-Id'] = state.orgId;
    const presignResp = await fetch('/api/chat/attachments/presign', {
      method: 'POST',
      headers: presignHeaders,
      body: JSON.stringify({ filename: file.name, contentType, conversationId: state.activeConversationId ?? state.conversationId, fileSizeBytes: file.size }),
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
    const msg = e?.message || "";
    if (msg.includes("not allowed") || msg.includes("exceeds the maximum")) {
      showRulesNotice("📎 " + msg);
    } else {
      alert(t("attachError") + msg);
    }
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

// ── Image lightbox ────────────────────────────────────────────────────────────
const imgLightbox = document.getElementById('imgLightbox');
const imgLightboxImg = document.getElementById('imgLightboxImg');

function openLightbox(src) {
  imgLightboxImg.src = src;
  imgLightbox.classList.remove('hidden');
}

imgLightbox.addEventListener('click', () => {
  imgLightbox.classList.add('hidden');
  imgLightboxImg.src = '';
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !imgLightbox.classList.contains('hidden')) {
    imgLightbox.classList.add('hidden');
    imgLightboxImg.src = '';
  }
});

// ── Enter to send ─────────────────────────────────────────────────────────────
messageInputEl.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    messageForm.requestSubmit();
  }
});

// ── Voice recording ───────────────────────────────────────────────────────────

const voiceBtn = document.getElementById('voiceBtn');
let mediaRecorder = null;
let voicePermissionTooltip = null;
let recordingChunks = [];
let recordingTimer = null;
let recordingSeconds = 0;
let voiceTimerEl = null;

function showVoicePermissionError(msg) {
  if (voicePermissionTooltip) voicePermissionTooltip.remove();
  voicePermissionTooltip = document.createElement('div');
  voicePermissionTooltip.className = 'voice-permission-error';
  voicePermissionTooltip.textContent = msg;
  voiceBtn.insertAdjacentElement('afterend', voicePermissionTooltip);
  // Auto-dismiss after 6 seconds
  setTimeout(() => {
    voicePermissionTooltip?.remove();
    voicePermissionTooltip = null;
  }, 6000);
}

function formatRecordingTime(secs) {
  const m = String(Math.floor(secs / 60)).padStart(2, '0');
  const s = String(secs % 60).padStart(2, '0');
  return `${m}:${s}`;
}

function startVoiceTimerDisplay() {
  recordingSeconds = 0;
  voiceTimerEl = document.createElement('span');
  voiceTimerEl.className = 'voice-timer';
  voiceTimerEl.textContent = formatRecordingTime(0);
  voiceBtn.insertAdjacentElement('afterend', voiceTimerEl);
  recordingTimer = setInterval(() => {
    recordingSeconds++;
    if (voiceTimerEl) voiceTimerEl.textContent = formatRecordingTime(recordingSeconds);
  }, 1000);
}

function stopVoiceTimerDisplay() {
  clearInterval(recordingTimer);
  recordingTimer = null;
  if (voiceTimerEl) { voiceTimerEl.remove(); voiceTimerEl = null; }
}

async function startRecording() {
  if (!navigator.mediaDevices?.getUserMedia) {
    showVoicePermissionError(t("noAudioSupport"));
    return;
  }

  // Check current permission state before calling getUserMedia so we can show
  // a helpful message when the user has previously blocked the microphone.
  if (navigator.permissions) {
    try {
      const status = await navigator.permissions.query({ name: 'microphone' });
      if (status.state === 'denied') {
        showVoicePermissionError(t("micBlocked"));
        return;
      }
    } catch {
      // Permissions API not supported — proceed and let getUserMedia handle it
    }
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mimeType = ['audio/webm', 'audio/ogg', 'audio/mp4'].find(t => MediaRecorder.isTypeSupported(t)) || '';
    mediaRecorder = new MediaRecorder(stream, mimeType ? { mimeType } : {});
    recordingChunks = [];
    mediaRecorder.ondataavailable = e => { if (e.data.size > 0) recordingChunks.push(e.data); };
    mediaRecorder.onstop = () => {
      stream.getTracks().forEach(t => t.stop());
      handleRecordingFinished();
    };
    mediaRecorder.start();
    voiceBtn.classList.add('recording');
    voiceBtn.setAttribute('aria-pressed', 'true');
    voiceBtn.title = t("stopRecording");
    startVoiceTimerDisplay();
  } catch (err) {
    if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
      showVoicePermissionError(t("micDenied"));
    } else if (err.name === 'NotFoundError') {
      showVoicePermissionError(t("noMic"));
    } else {
      showVoicePermissionError(t("micError") + err.message);
    }
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop();
  }
  const btn = document.getElementById('voiceBtn');
  if (btn) {
    btn.classList.remove('recording');
    btn.setAttribute('aria-pressed', 'false');
    btn.title = t("voice");
  }
  stopVoiceTimerDisplay();
}

async function handleRecordingFinished() {
  if (recordingChunks.length === 0) return;
  const mimeType = recordingChunks[0].type || 'audio/webm';
  const ext = mimeType.includes('ogg') ? 'ogg' : mimeType.includes('mp4') ? 'mp4' : 'webm';
  const blob = new Blob(recordingChunks, { type: mimeType });
  const file = new File([blob], `voice-${Date.now()}.${ext}`, { type: mimeType });

  if (file.size > 50 * 1024 * 1024) { alert(t("recordTooBig")); return; }

  state.presignInProgress = true;
  try {
    if (state.token && Date.now() > state.tokenExpiresAt) {
      const ok = await refreshAccessToken();
      if (!ok) { logout(); return; }
    }
    const presignHeaders2 = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${state.token}` };
    if (state.orgId) presignHeaders2['X-Org-Id'] = state.orgId;
    const presignResp = await fetch('/api/chat/attachments/presign', {
      method: 'POST',
      headers: presignHeaders2,
      body: JSON.stringify({ filename: file.name, contentType: mimeType, conversationId: state.activeConversationId ?? state.conversationId, fileSizeBytes: file.size }),
    });
    if (!presignResp.ok) throw new Error(await presignResp.text());
    const data = await presignResp.json();
    if (!data?.uploadUrl || !data?.publicUrl) throw new Error('Respuesta de presign inválida');

    // Clear any existing attachment and show audio preview
    const attachmentPreview = document.getElementById('attachmentPreview');
    const oldImg = attachmentPreview.querySelector('img');
    if (oldImg && oldImg.src.startsWith('blob:')) URL.revokeObjectURL(oldImg.src);
    attachmentPreview.innerHTML = '';
    attachmentPreview.classList.remove('hidden');

    const audio = document.createElement('audio');
    audio.src = URL.createObjectURL(blob);
    audio.controls = true;
    audio.style.maxWidth = '220px';
    audio.style.height = '36px';
    attachmentPreview.appendChild(audio);

    const removeBtn = document.createElement('button');
    removeBtn.textContent = '✕';
    removeBtn.type = 'button';
    removeBtn.className = 'remove-attachment';
    removeBtn.onclick = () => {
      URL.revokeObjectURL(audio.src);
      state.pendingAttachment = null;
      attachmentPreview.classList.add('hidden');
      attachmentPreview.innerHTML = '';
    };
    attachmentPreview.appendChild(removeBtn);

    state.pendingAttachment = { file, uploadUrl: data.uploadUrl, publicUrl: data.publicUrl, attachmentType: data.attachmentType || 'AUDIO' };
  } catch (e) {
    alert(t("audioError") + e.message);
  } finally {
    state.presignInProgress = false;
  }
}

voiceBtn.addEventListener('click', () => {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    stopRecording();
  } else {
    stopRecording(); // reset if in odd state
    startRecording();
  }
});

// ── Mobile sidebar toggle ─────────────────────────────────────────────────────

const sidebarToggleBtn = document.getElementById('sidebarToggle');
const convSidebarEl = document.querySelector('.conv-sidebar');
const sidebarBackdropEl = document.getElementById('sidebarBackdrop');

function closeMobileSidebar() {
  convSidebarEl?.classList.remove('open');
  sidebarBackdropEl?.classList.remove('open');
  sidebarToggleBtn?.setAttribute('aria-expanded', 'false');
}

function toggleMobileSidebar() {
  const isOpen = convSidebarEl?.classList.toggle('open');
  sidebarBackdropEl?.classList.toggle('open', isOpen);
  sidebarToggleBtn?.setAttribute('aria-expanded', String(!!isOpen));
}

sidebarToggleBtn?.addEventListener('click', toggleMobileSidebar);
sidebarBackdropEl?.addEventListener('click', closeMobileSidebar);

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
    alert(t("dmError") + (e?.message || String(e)));
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
    state.orgId = parsed.orgId || "";
    state.systemRole = parsed.systemRole || "";
    if (Number.isInteger(parsed.conversationId) && parsed.conversationId > 0) {
      state.conversationId = parsed.conversationId;
    }

    // Check expiry; attempt refresh if expired
    if (Date.now() > state.tokenExpiresAt) {
      const ok = await refreshAccessToken();
      if (!ok) { logout(); return; }
    }

    await fetchUserProfile();
    const consentOk = await checkConsent();
    if (!consentOk) return;

    await ensureConversation();
    setSessionInfo();
    setPanels(true);
    await loadHistory();
    await loadConversations();
    connectWebSocket();
    saveSession();
  } catch {
    logout();
  }
}

// ── Language switcher wiring ──────────────────────────────────────────────────

["authLangSelect", "langSelect"].forEach(id => {
  const sel = document.getElementById(id);
  if (sel) sel.addEventListener("change", e => setLang(e.target.value));
});

// ── Bootstrap ─────────────────────────────────────────────────────────────────

applyTranslations();
setSocketStatus(false);
setPanels(false);
renderPresence([]);
renderTypingIndicator();
restoreSession();
