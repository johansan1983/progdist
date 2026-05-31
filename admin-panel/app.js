"use strict";

// ── Constants ─────────────────────────────────────────────────────────────
const KEYCLOAK_TOKEN_URL = "/kc/realms/superchat/protocol/openid-connect/token";
const KEYCLOAK_CLIENT_ID = "superchat-frontend";
const SESSION_KEY        = "superchat.admin.session";

// ── State ─────────────────────────────────────────────────────────────────
const state = {
  token: "",
  refreshToken: "",
  tokenExpiresAt: 0,
  username: "",
  activeOrgId: null,
  activeOrgName: "",
  depts: [],
  incidentPage: 0,
  incidentTotal: 0,
  auditPage: 0,
  auditTotal: 0,
  pendingRuleChanges: {},
};

// ── Session ───────────────────────────────────────────────────────────────
function saveSession() {
  localStorage.setItem(SESSION_KEY, JSON.stringify({
    token: state.token,
    refreshToken: state.refreshToken,
    tokenExpiresAt: state.tokenExpiresAt,
    username: state.username,
  }));
}

function loadSession() {
  try {
    const s = JSON.parse(localStorage.getItem(SESSION_KEY) || "{}");
    if (s.token) {
      Object.assign(state, s);
      return true;
    }
  } catch { /* ignore */ }
  return false;
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY);
  state.token = "";
  state.refreshToken = "";
  state.tokenExpiresAt = 0;
  state.username = "";
}

// ── Token helpers ─────────────────────────────────────────────────────────
function decodeJwt(token) {
  try {
    return JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
  } catch { return null; }
}

async function refreshToken() {
  if (!state.refreshToken) return false;
  try {
    const r = await fetch(KEYCLOAK_TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: KEYCLOAK_CLIENT_ID,
        refresh_token: state.refreshToken,
      }),
    });
    if (!r.ok) return false;
    applyTokenResponse(await r.json());
    saveSession();
    return true;
  } catch { return false; }
}

function applyTokenResponse(data) {
  state.token = data.access_token;
  state.refreshToken = data.refresh_token;
  state.tokenExpiresAt = Date.now() + (data.expires_in - 30) * 1000;
  const payload = decodeJwt(state.token);
  state.username = payload?.preferred_username || "";
}

// ── API helper ────────────────────────────────────────────────────────────
async function api(path, { method = "GET", body, orgId } = {}) {
  if (Date.now() >= state.tokenExpiresAt) await refreshToken();
  const headers = {
    "Authorization": `Bearer ${state.token}`,
    "Content-Type": "application/json",
  };
  if (orgId) headers["X-Org-Id"] = orgId;
  const opts = { method, headers };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const r = await fetch("/api" + path, opts);
  if (!r.ok) {
    const text = await r.text().catch(() => r.statusText);
    throw new Error(`${r.status} ${text}`);
  }
  if (r.status === 204) return null;
  return r.json();
}

// ── Auth ──────────────────────────────────────────────────────────────────
async function login(username, password) {
  const r = await fetch(KEYCLOAK_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      client_id: KEYCLOAK_CLIENT_ID,
      username,
      password,
    }),
  });
  if (!r.ok) throw new Error("Invalid credentials");
  applyTokenResponse(await r.json());
  saveSession();
}

// ── UI helpers ────────────────────────────────────────────────────────────
const $ = id => document.getElementById(id);
function show(id)  { $(id).classList.remove("hidden"); }
function hide(id)  { $(id).classList.add("hidden"); }
function showEl(el) { el.classList.remove("hidden"); }
function hideEl(el) { el.classList.add("hidden"); }

function setError(id, msg) {
  const el = $(id);
  el.textContent = msg;
  el.classList.toggle("hidden", !msg);
}

function badge(text, colour) {
  return `<span class="badge badge-${colour}">${text}</span>`;
}

function severityBadge(s) {
  return badge(s, s === "HIGH" ? "red" : s === "MEDIUM" ? "yellow" : "gray");
}

function actionBadge(a) {
  return badge(a, a === "BLOCK" ? "red" : a === "REPLACE" ? "yellow" : "blue");
}

function roleBadge(r) {
  const map = { PLATFORM_ADMIN: "red", ORG_ADMIN: "blue", DEPT_ADMIN: "yellow", USER: "gray" };
  return badge(r, map[r] || "gray");
}

function fmtDate(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" });
}

// ── Tab switching ─────────────────────────────────────────────────────────
function switchTab(tabId) {
  document.querySelectorAll(".tab-btn").forEach(b => b.classList.toggle("active", b.dataset.tab === tabId));
  document.querySelectorAll(".tab-content").forEach(s => s.classList.toggle("hidden", s.id !== "tab-" + tabId));
}

// ── Org selector ──────────────────────────────────────────────────────────
async function loadOrgSelector() {
  try {
    const orgs = await api("/organizations");
    const sel = $("orgSelector");
    sel.innerHTML = `<option value="">— select organization —</option>` +
      orgs.map(o => `<option value="${o.id}">${escHtml(o.name)}</option>`).join("");
    if (state.activeOrgId) sel.value = state.activeOrgId;
  } catch (e) {
    console.error("Failed to load orgs", e);
  }
}

function escHtml(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

async function onOrgSelected(orgId) {
  state.activeOrgId = orgId || null;
  state.activeOrgName = $("orgSelector").options[$("orgSelector").selectedIndex]?.text || "";
  if (orgId) {
    await Promise.all([
      loadDashboard(),
      loadDepts(orgId),
    ]);
  }
}

// ── Dashboard ─────────────────────────────────────────────────────────────
async function loadDashboard() {
  const orgId = state.activeOrgId;
  if (!orgId) { hide("dashboardCards"); return; }
  show("dashboardCards");
  try {
    const [users, rules, incidents, erasure] = await Promise.allSettled([
      api(`/organizations/${orgId}/users`),
      api(`/admin/organizations/${orgId}/rules`),
      api(`/moderation/organizations/${orgId}/incidents?size=1`),
      api(`/compliance/audit?orgId=${orgId}&size=1`),
    ]);
    $("dashUserCount").textContent     = users.value?.length ?? "—";
    $("dashRulesCount").textContent    = rules.value?.length ?? "—";
    $("dashIncidentCount").textContent = incidents.value?.totalElements ?? "—";
    $("dashErasureCount").textContent  = erasure.value?.totalElements ?? "—";
  } catch { /* show — on error */ }
}

// ── Organizations tab ─────────────────────────────────────────────────────
async function loadOrgs() {
  const tbody = $("orgTableBody");
  tbody.innerHTML = `<tr><td colspan="5" class="empty">Loading…</td></tr>`;
  try {
    const orgs = await api("/organizations");
    if (!orgs.length) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty">No organizations yet.</td></tr>`;
      return;
    }
    tbody.innerHTML = orgs.map(o => `
      <tr>
        <td>${escHtml(o.name)}</td>
        <td><code>${escHtml(o.slug)}</code></td>
        <td>${badge(o.plan, o.plan === "UNIVERSITY" ? "yellow" : o.plan === "ENTERPRISE" ? "blue" : "gray")}</td>
        <td>${fmtDate(o.createdAt)}</td>
        <td>
          <button class="btn-ghost" data-action="depts" data-id="${escHtml(o.id)}" data-name="${escHtml(o.name)}">Departments</button>
        </td>
      </tr>`).join("");
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

async function createOrg() {
  const name = $("newOrgName").value.trim();
  const slug = $("newOrgSlug").value.trim();
  const plan = $("newOrgPlan").value;
  setError("orgFormError", "");
  if (!name || !slug) { setError("orgFormError", "Name and slug are required."); return; }
  try {
    await api("/organizations", { method: "POST", body: { name, slug, plan } });
    hide("newOrgForm");
    $("newOrgName").value = "";
    $("newOrgSlug").value = "";
    await loadOrgs();
    await loadOrgSelector();
  } catch (e) { setError("orgFormError", e.message); }
}

// ── Departments ───────────────────────────────────────────────────────────
async function loadDepts(orgId) {
  try {
    state.depts = await api(`/organizations/${orgId}/departments`);
  } catch { state.depts = []; }
}

function openDeptPanel(orgId, orgName) {
  $("deptPanelTitle").textContent = `Departments — ${orgName}`;
  $("deptPanel").dataset.orgId = orgId;
  show("deptPanel");
  renderDepts(orgId);
}

async function renderDepts(orgId) {
  await loadDepts(orgId);
  const tbody = $("deptTableBody");
  const deptMap = Object.fromEntries(state.depts.map(d => [d.id, d.name]));
  tbody.innerHTML = state.depts.length
    ? state.depts.map(d => `
        <tr>
          <td>${escHtml(d.name)}</td>
          <td>${d.parentDeptId ? escHtml(deptMap[d.parentDeptId] || d.parentDeptId) : "—"}</td>
          <td><span class="mono">${escHtml(d.id)}</span></td>
        </tr>`).join("")
    : `<tr><td colspan="3" class="empty">No departments yet.</td></tr>`;

  // Refresh parent selector
  $("newDeptParent").innerHTML = `<option value="">— none (top-level) —</option>` +
    state.depts.map(d => `<option value="${d.id}">${escHtml(d.name)}</option>`).join("");
}

async function createDept() {
  const orgId = $("deptPanel").dataset.orgId;
  const name   = $("newDeptName").value.trim();
  const parentDeptId = $("newDeptParent").value || null;
  if (!name) return;
  try {
    await api(`/organizations/${orgId}/departments`, { method: "POST", body: { name, parentDeptId } });
    hide("newDeptForm");
    $("newDeptName").value = "";
    await renderDepts(orgId);
    await loadDepts(orgId);
    if (state.activeOrgId === orgId) populateAssignDeptSelect();
  } catch (e) { alert(e.message); }
}

// ── Users tab ─────────────────────────────────────────────────────────────
async function loadOrgUsers() {
  const orgId = state.activeOrgId;
  if (!orgId) { $("usersHint").textContent = "Select an organization first."; show("usersHint"); return; }
  hide("usersHint");
  const tbody = $("usersTableBody");
  tbody.innerHTML = `<tr><td colspan="5" class="empty">Loading…</td></tr>`;
  try {
    const users = await api(`/organizations/${orgId}/users`);
    renderUsersTable(users, orgId);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

async function searchUsers() {
  const q = $("userSearchInput").value.trim();
  if (!q) return;
  const tbody = $("usersTableBody");
  tbody.innerHTML = `<tr><td colspan="5" class="empty">Searching…</td></tr>`;
  try {
    const users = await api(`/users/search?q=${encodeURIComponent(q)}`);
    renderUsersTable(users, state.activeOrgId);
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

function renderUsersTable(users, orgId) {
  const tbody = $("usersTableBody");
  if (!users.length) { tbody.innerHTML = `<tr><td colspan="5" class="empty">No users found.</td></tr>`; return; }
  tbody.innerHTML = users.map(u => `
    <tr>
      <td>${escHtml(u.displayName)}</td>
      <td>${roleBadge(u.systemRole)}</td>
      <td><span class="mono">${u.orgId ? escHtml(u.orgId) : "—"}</span></td>
      <td><span class="mono">${u.deptId ? escHtml(u.deptId) : "—"}</span></td>
      <td>
        ${orgId ? `<button class="btn-ghost" data-action="assign" data-id="${escHtml(u.id)}" data-org="${escHtml(orgId)}" data-role="${escHtml(u.systemRole)}" data-dept="${escHtml(u.deptId || '')}">Assign</button>` : ""}
      </td>
    </tr>`).join("");
}

function openAssignModal(userId, orgId, currentRole, currentDeptId) {
  $("assignUserId").value = userId;
  $("assignModal").dataset.orgId = orgId;
  $("assignRole").value = currentRole || "USER";
  setError("assignError", "");
  populateAssignDeptSelect(currentDeptId);
  show("assignModal");
}

function populateAssignDeptSelect(selectedDeptId) {
  const sel = $("assignDept");
  sel.innerHTML = `<option value="">— none —</option>` +
    state.depts.map(d => `<option value="${d.id}" ${d.id === selectedDeptId ? "selected" : ""}>${escHtml(d.name)}</option>`).join("");
}

async function saveAssignment() {
  const userId = $("assignUserId").value;
  const orgId  = $("assignModal").dataset.orgId;
  const role   = $("assignRole").value;
  const deptId = $("assignDept").value || null;
  setError("assignError", "");
  try {
    await api(`/organizations/${orgId}/users/${userId}`, { method: "PUT", body: { systemRole: role, deptId } });
    hide("assignModal");
    await loadOrgUsers();
  } catch (e) { setError("assignError", e.message); }
}

// ── Business rules tab ────────────────────────────────────────────────────
const RULE_META = {
  message_retention_days:  { label: "Message retention (days)", type: "number", desc: "Days before messages are anonymised. 365 = 1 year, 2555 ≈ 7 years (FERPA)." },
  max_file_size_mb:        { label: "Max file size (MB)", type: "number", desc: "Maximum upload size per attachment." },
  allowed_file_types:      { label: "Allowed file types", type: "text",   desc: "Comma-separated MIME prefixes, e.g. image/,application/pdf" },
  working_hours_only:      { label: "Working hours only", type: "select", options: ["false","true"], desc: "Block messages outside business hours." },
  working_hours_start:     { label: "Working hours start", type: "text",  desc: "HH:mm — e.g. 08:00" },
  working_hours_end:       { label: "Working hours end",   type: "text",  desc: "HH:mm — e.g. 18:00" },
  working_hours_timezone:  { label: "Timezone",            type: "text",  desc: "IANA zone, e.g. America/Bogota" },
  require_consent_on_join: { label: "Require consent on join", type: "select", options: ["true","false"], desc: "User must accept data policy before joining." },
  consent_version:         { label: "Consent version", type: "number", desc: "Bump this number to force all users to re-consent." },
  dm_enabled:              { label: "Direct messages enabled", type: "select", options: ["true","false"], desc: "Allow private 1-to-1 messages." },
  guest_access_enabled:    { label: "Guest access", type: "select", options: ["false","true"], desc: "Allow users without an org account to join public channels." },
};

async function loadRules() {
  const orgId = state.activeOrgId;
  if (!orgId) { show("rulesHint"); hide("rulesGrid"); return; }
  hide("rulesHint");
  try {
    const rules = await api(`/admin/organizations/${orgId}/rules`);
    const existing = Object.fromEntries(rules.map(r => [r.key, r.value]));
    state.pendingRuleChanges = { ...existing };
    renderRulesGrid(existing);
    show("rulesGrid");
  } catch (e) {
    $("rulesHint").textContent = "Failed to load rules: " + e.message;
    show("rulesHint");
  }
}

function renderRulesGrid(existing) {
  const grid = $("rulesGrid");
  grid.innerHTML = Object.entries(RULE_META).map(([key, meta]) => {
    const val = existing[key] ?? "";
    let input;
    if (meta.type === "select") {
      input = `<select data-key="${key}" onchange="state.pendingRuleChanges['${key}']=this.value">` +
        meta.options.map(o => `<option value="${o}" ${val === o ? "selected" : ""}>${o}</option>`).join("") +
        `</select>`;
    } else {
      input = `<input type="${meta.type}" data-key="${key}" value="${escHtml(val)}"
                 oninput="state.pendingRuleChanges['${key}']=this.value" />`;
    }
    return `
      <div class="rule-card">
        <label>${escHtml(meta.label)}</label>
        ${input}
        <p class="rule-desc">${escHtml(meta.desc)}</p>
      </div>`;
  }).join("");
}

async function saveRules() {
  const orgId = state.activeOrgId;
  if (!orgId) return;
  try {
    await Promise.all(
      Object.entries(state.pendingRuleChanges).map(([key, value]) =>
        api(`/admin/organizations/${orgId}/rules/${key}`, { method: "PUT", body: { value } })
      )
    );
    alert("Rules saved.");
  } catch (e) { alert("Save failed: " + e.message); }
}

async function seedDefaults() {
  const orgId = state.activeOrgId;
  if (!orgId) return;
  try {
    await api(`/admin/organizations/${orgId}/rules/seed-defaults`, { method: "POST" });
    await loadRules();
  } catch (e) { alert("Seed failed: " + e.message); }
}

// ── Moderation tab ────────────────────────────────────────────────────────
async function loadWordList() {
  const orgId = state.activeOrgId;
  if (!orgId) return;
  const tbody = $("wordTableBody");
  tbody.innerHTML = `<tr><td colspan="7" class="empty">Loading…</td></tr>`;
  try {
    const rules = await api(`/moderation/organizations/${orgId}/word-lists`);
    if (!rules.length) { tbody.innerHTML = `<tr><td colspan="7" class="empty">No rules yet.</td></tr>`; return; }
    tbody.innerHTML = rules.map(r => `
      <tr>
        <td><code>${escHtml(r.pattern)}</code></td>
        <td>${r.regex ? badge("regex","blue") : ""}</td>
        <td>${severityBadge(r.severity)}</td>
        <td>${actionBadge(r.action)}</td>
        <td>${escHtml(r.replacement || "")}</td>
        <td>${fmtDate(r.createdAt)}</td>
        <td><button class="btn-danger" data-action="del-word" data-org="${escHtml(orgId)}" data-id="${escHtml(r.id)}">Remove</button></td>
      </tr>`).join("");
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

async function deleteWordRule(orgId, ruleId) {
  if (!confirm("Remove this moderation rule?")) return;
  try {
    await api(`/moderation/organizations/${orgId}/word-lists/${ruleId}`, { method: "DELETE" });
    await loadWordList();
  } catch (e) { alert(e.message); }
}

async function addWordRule() {
  const orgId = state.activeOrgId;
  if (!orgId) { alert("Select an organization first."); return; }
  setError("wordFormError", "");
  const pattern = $("wordPattern").value.trim();
  if (!pattern) { setError("wordFormError", "Pattern is required."); return; }
  try {
    await api(`/moderation/organizations/${orgId}/word-lists`, {
      method: "POST",
      body: {
        pattern,
        regex: $("wordIsRegex").checked,
        severity: $("wordSeverity").value,
        action: $("wordAction").value,
        replacement: $("wordReplacement").value.trim() || null,
      },
    });
    hide("addWordForm");
    $("wordPattern").value = "";
    $("wordReplacement").value = "";
    $("wordIsRegex").checked = false;
    await loadWordList();
  } catch (e) { setError("wordFormError", e.message); }
}

async function loadIncidents(page = 0) {
  const orgId = state.activeOrgId;
  if (!orgId) return;
  const userId = $("incidentUserFilter").value.trim();
  const url = `/moderation/organizations/${orgId}/incidents?page=${page}&size=20` + (userId ? `&userId=${encodeURIComponent(userId)}` : "");
  const tbody = $("incidentTableBody");
  tbody.innerHTML = `<tr><td colspan="5" class="empty">Loading…</td></tr>`;
  try {
    const data = await api(url);
    state.incidentPage  = data.page;
    state.incidentTotal = data.totalPages;
    if (!data.content.length) { tbody.innerHTML = `<tr><td colspan="5" class="empty">No incidents.</td></tr>`; hide("incidentPager"); return; }
    tbody.innerHTML = data.content.map(i => `
      <tr>
        <td>${fmtDate(i.createdAt)}</td>
        <td><span class="mono">${escHtml(i.userId)}</span></td>
        <td>${i.conversationId ?? "—"}</td>
        <td><code>${escHtml(i.matchedPattern || "")}</code></td>
        <td>${actionBadge(i.actionTaken)}</td>
      </tr>`).join("");
    $("incidentPageInfo").textContent = `Page ${data.page + 1} of ${data.totalPages}`;
    show("incidentPager");
    $("incidentPrevBtn").disabled = data.page === 0;
    $("incidentNextBtn").disabled = data.page + 1 >= data.totalPages;
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

// ── Compliance tab ────────────────────────────────────────────────────────
async function loadAuditLog(page = 0) {
  const orgId  = state.activeOrgId;
  const actor  = $("auditActorFilter").value.trim();
  const params = new URLSearchParams({ page, size: 50 });
  if (actor) params.set("actorId", actor);
  else if (orgId) params.set("orgId", orgId);

  const tbody = $("auditTableBody");
  tbody.innerHTML = `<tr><td colspan="5" class="empty">Loading…</td></tr>`;
  try {
    const data = await api(`/compliance/audit?${params}`);
    state.auditPage  = data.page;
    state.auditTotal = data.totalPages;
    if (!data.content.length) { tbody.innerHTML = `<tr><td colspan="5" class="empty">No entries.</td></tr>`; hide("auditPager"); return; }
    tbody.innerHTML = data.content.map(a => `
      <tr>
        <td>${fmtDate(a.createdAt)}</td>
        <td><code>${escHtml(a.eventType)}</code></td>
        <td><span class="mono">${escHtml(a.actorId)}</span></td>
        <td><span class="mono">${escHtml(a.targetId || "")}</span></td>
        <td><code>${escHtml(JSON.stringify(a.payload || {})).slice(0, 80)}</code></td>
      </tr>`).join("");
    $("auditPageInfo").textContent = `Page ${data.page + 1} of ${data.totalPages}`;
    show("auditPager");
    $("auditPrevBtn").disabled = data.page === 0;
    $("auditNextBtn").disabled = data.page + 1 >= data.totalPages;
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

async function loadErasureRequests() {
  const tbody = $("erasureTableBody");
  tbody.innerHTML = `<tr><td colspan="6" class="empty">Loading…</td></tr>`;
  try {
    // Platform admin view — load pending from all users via compliance service
    const data = await api(`/compliance/erasure`);
    if (!data.length) { tbody.innerHTML = `<tr><td colspan="6" class="empty">No erasure requests.</td></tr>`; return; }
    tbody.innerHTML = data.map(e => `
      <tr>
        <td><span class="mono">${escHtml(e.userId)}</span></td>
        <td><span class="mono">${escHtml(e.orgId || "—")}</span></td>
        <td>${badge(e.status, e.status === "COMPLETED" ? "green" : e.status === "IN_PROGRESS" ? "yellow" : "gray")}</td>
        <td>${fmtDate(e.requestedAt)}</td>
        <td>${fmtDate(e.completedAt)}</td>
        <td>
          ${e.status !== "COMPLETED" ? `<button class="btn-secondary" data-action="complete-erasure" data-id="${escHtml(e.id)}">Mark complete</button>` : ""}
        </td>
      </tr>`).join("");
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty error">${escHtml(e.message)}</td></tr>`;
  }
}

async function completeErasure(requestId) {
  if (!confirm("Mark this erasure request as complete? This cannot be undone.")) return;
  try {
    await api(`/compliance/erasure/${requestId}/complete`, { method: "PUT" });
    await loadErasureRequests();
  } catch (e) { alert(e.message); }
}

// ── Bootstrap ─────────────────────────────────────────────────────────────
function showAdminPanel() {
  hide("authPanel");
  show("adminPanel");
  $("headerUser").textContent = state.username;
  loadOrgSelector();
  loadDashboard();
}

document.addEventListener("DOMContentLoaded", () => {

  // Restore session
  if (loadSession() && state.token) {
    showAdminPanel();
  }

  // Login
  $("loginForm").addEventListener("submit", async e => {
    e.preventDefault();
    setError("authError", "");
    const btn = $("loginBtn");
    btn.disabled = true;
    btn.textContent = "Signing in…";
    try {
      await login($("username").value, $("password").value);
      showAdminPanel();
    } catch (err) {
      setError("authError", err.message);
    } finally {
      btn.disabled = false;
      btn.textContent = "Sign in";
    }
  });

  // Logout
  $("logoutBtn").addEventListener("click", () => {
    clearSession();
    location.reload();
  });

  // Tab switching
  document.querySelectorAll(".tab-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      const tab = btn.dataset.tab;
      switchTab(tab);
      if (tab === "organizations") loadOrgs();
      if (tab === "users")         loadOrgUsers();
      if (tab === "rules")         loadRules();
      if (tab === "moderation")    { loadWordList(); loadIncidents(); }
      if (tab === "compliance")    { loadAuditLog(); loadErasureRequests(); }
    });
  });

  // Delegated table actions — avoids building onclick="" handlers from data
  // (which is XSS-prone: a malicious org/dept/user name could break out of the
  // attribute string). Values arrive safely via dataset.
  document.addEventListener("click", (ev) => {
    const btn = ev.target.closest("button[data-action]");
    if (!btn) return;
    const d = btn.dataset;
    switch (d.action) {
      case "depts":            openDeptPanel(d.id, d.name); break;
      case "assign":           openAssignModal(d.id, d.org, d.role, d.dept); break;
      case "del-word":         deleteWordRule(d.org, d.id); break;
      case "complete-erasure": completeErasure(d.id); break;
    }
  });

  // Org selector
  $("orgSelector").addEventListener("change", e => onOrgSelected(e.target.value));

  // ── Organizations ──
  $("newOrgBtn").addEventListener("click", () => { show("newOrgForm"); });
  $("cancelOrgBtn").addEventListener("click", () => { hide("newOrgForm"); setError("orgFormError", ""); });
  $("createOrgBtn").addEventListener("click", createOrg);

  // Auto-fill slug from name
  $("newOrgName").addEventListener("input", () => {
    $("newOrgSlug").value = $("newOrgName").value.trim().toLowerCase().replace(/\s+/g, "-").replace(/[^a-z0-9-]/g, "");
  });

  // ── Departments ──
  $("newDeptBtn").addEventListener("click", () => { show("newDeptForm"); });
  $("cancelDeptBtn").addEventListener("click", () => { hide("newDeptForm"); });
  $("createDeptBtn").addEventListener("click", createDept);

  // ── Users ──
  $("userSearchBtn").addEventListener("click", searchUsers);
  $("userSearchInput").addEventListener("keydown", e => { if (e.key === "Enter") searchUsers(); });
  $("assignSaveBtn").addEventListener("click", saveAssignment);
  $("assignCancelBtn").addEventListener("click", () => hide("assignModal"));

  // ── Rules ──
  $("saveRulesBtn").addEventListener("click", saveRules);
  $("seedDefaultsBtn").addEventListener("click", seedDefaults);

  // ── Moderation ──
  $("addWordBtn").addEventListener("click", () => { show("addWordForm"); });
  $("cancelWordBtn").addEventListener("click", () => { hide("addWordForm"); setError("wordFormError", ""); });
  $("saveWordBtn").addEventListener("click", addWordRule);
  $("incidentLoadBtn").addEventListener("click", () => loadIncidents(0));
  $("incidentPrevBtn").addEventListener("click", () => loadIncidents(state.incidentPage - 1));
  $("incidentNextBtn").addEventListener("click", () => loadIncidents(state.incidentPage + 1));

  // ── Compliance ──
  $("auditLoadBtn").addEventListener("click", () => loadAuditLog(0));
  $("auditPrevBtn").addEventListener("click", () => loadAuditLog(state.auditPage - 1));
  $("auditNextBtn").addEventListener("click", () => loadAuditLog(state.auditPage + 1));
  $("erasureLoadBtn").addEventListener("click", loadErasureRequests);
});
