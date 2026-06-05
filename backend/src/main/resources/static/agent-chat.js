const ACCESS_TOKEN_KEY  = "jagent.accessToken";
const REFRESH_TOKEN_KEY = "jagent.refreshToken";
const AGENT_ID_KEY      = "jagent.currentAgentId";
const PENDING_AGENT_PROMPT_KEY = "jagent.pendingAgentPrompt";

let accessToken = "";
let refreshToken = "";
let currentAgentId = "";
let currentSessionId = "";
let currentAgent = null;
let isSending = false;

const messageList  = document.getElementById("messageList");
const chatForm     = document.getElementById("chatForm");
const messageInput = document.getElementById("messageInput");
const sessionHint  = document.getElementById("sessionHint");
const modelHint    = document.getElementById("modelHint");
const sessionMeta  = document.getElementById("sessionMeta");
const sessionList  = document.getElementById("sessionList");
const sendButton   = document.getElementById("sendButton");
const quickActionButtons = Array.from(document.querySelectorAll(".quick-action-button"));

function loadTokens() {
  accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || "";
  refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || "";
  currentAgentId = localStorage.getItem(AGENT_ID_KEY) || "";
  currentSessionId = currentAgentId ? (localStorage.getItem(getAgentSessionStorageKey(currentAgentId)) || "") : "";
}

function getAgentSessionStorageKey(agentId) {
  return `jagent.agentSession.${agentId || "default"}`;
}

function saveTokens(a, r) {
  accessToken = a || "";
  refreshToken = r || "";
  accessToken ? localStorage.setItem(ACCESS_TOKEN_KEY, accessToken) : localStorage.removeItem(ACCESS_TOKEN_KEY);
  refreshToken ? localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken) : localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function saveCurrentSessionId(id) {
  currentSessionId = id || "";
  if (!currentAgentId) return;
  const storageKey = getAgentSessionStorageKey(currentAgentId);
  currentSessionId ? localStorage.setItem(storageKey, currentSessionId)
                   : localStorage.removeItem(storageKey);
}

function clearTokens() {
  saveTokens("", "");
  saveCurrentSessionId("");
  localStorage.removeItem(AGENT_ID_KEY);
}

function redirectToLogin() { window.location.href = "/"; }

function shouldRefresh(data) {
  if (!data?.message) return false;
  return /jwt|token|expired|signature|malformed|claims/i.test(data.message);
}

async function callApi(url, payload, options = {}) {
  const res = await fetch(url, {
    method: options.method || "POST",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    body: payload ? JSON.stringify(payload) : undefined,
  });
  return res.json();
}

async function refreshAccessToken() {
  if (!refreshToken) { clearTokens(); return null; }
  const data = await callApi("/api/auth/refresh", { refreshToken });
  if (data.code === 200 && data.data) {
    saveTokens(data.data.accessToken, data.data.refreshToken);
    return data;
  }
  clearTokens();
  return null;
}

async function authorizedCall(url, payload, options = {}) {
  if (!accessToken) { redirectToLogin(); return null; }
  let data = await callApi(url, payload, {
    ...options,
    headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` },
  });
  if (shouldRefresh(data)) {
    const ok = await refreshAccessToken();
    if (!ok) { redirectToLogin(); return null; }
    data = await callApi(url, payload, {
      ...options,
      headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` },
    });
  }
  return data;
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function formatDate(iso) {
  return new Date(iso).toLocaleString("zh-CN",
    { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

function pageIcon() {
  return `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor"
               stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 2h10a1 1 0 011 1v10a1 1 0 01-1 1H3a1 1 0 01-1-1V3a1 1 0 011-1z"/>
            <path d="M5 6h6M5 9h4"/>
          </svg>`;
}

function clearMessages() {
  messageList.innerHTML = `
    <div class="message-placeholder">
      <div class="message-placeholder-icon">
        <svg viewBox="0 0 40 40" fill="none" stroke="currentColor"
             stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M36 6H4a2 2 0 00-2 2v20a2 2 0 002 2h8l8 8 8-8h8a2 2 0 002-2V8a2 2 0 00-2-2z"/>
        </svg>
      </div>
      <h3>新建 Agent 会话</h3>
      <p>发送第一条消息开始。<br>你可以先测试：请在工作区搜索文件名包含 <code>photo</code> 的文本文件，读取最匹配的文件并总结三个重点。</p>
    </div>`;
}

function appendMessage(role, content) {
  const ph = messageList.querySelector(".message-placeholder");
  if (ph) ph.remove();
  const isUser = role === "user";
  const isTool = role === "tool";
  const item = document.createElement("div");
  item.className = `message-item ${isUser ? "user" : isTool ? "tool" : "assistant"}`;
  const avatarLabel = isUser ? "You" : isTool ? "Tool" : "AI";
  const bubblePrefix = isTool ? `<div class="tool-badge">工具执行记录</div>` : "";
  item.innerHTML = `
    <div class="message-avatar ${isUser ? "user" : isTool ? "tool" : "assistant"}">${avatarLabel}</div>
    <div class="message-bubble">${bubblePrefix}${escapeHtml(content)}</div>`;
  messageList.appendChild(item);
  messageList.scrollTop = messageList.scrollHeight;
}

function appendTyping() {
  const ph = messageList.querySelector(".message-placeholder");
  if (ph) ph.remove();
  const item = document.createElement("div");
  item.className = "message-item assistant";
  item.id = "__typing__";
  item.innerHTML = `
    <div class="message-avatar assistant">AI</div>
    <div class="message-bubble">
      <div class="typing-dot"><span></span><span></span><span></span></div>
    </div>`;
  messageList.appendChild(item);
}

function removeTyping() {
  const el = document.getElementById("__typing__");
  if (el) el.remove();
}

async function loadAgent() {
  const data = await authorizedCall("/api/agents", null, { method: "GET" });
  if (!data || data.code !== 200 || !Array.isArray(data.data)) return null;
  currentAgent = data.data.find(a => a.id === currentAgentId) || data.data[0] || null;
  if (currentAgent) {
    currentAgentId = currentAgent.id;
    localStorage.setItem(AGENT_ID_KEY, currentAgentId);
    currentSessionId = localStorage.getItem(getAgentSessionStorageKey(currentAgentId)) || "";
    document.getElementById("agentTitle").textContent = currentAgent.name;
    const kbHint = currentAgent.knowledgeEnabled
      ? `已绑定知识库：${(currentAgent.allowedKnowledgeBaseNames || []).join("、")}`
      : "当前未绑定知识库";
    sessionHint.textContent = currentAgent.description
      ? `${currentAgent.description} · ${kbHint}`
      : kbHint;
    modelHint.textContent = `模型：${currentAgent.defaultModelKey}`;
  }
  return currentAgent;
}

function renderSessionList(sessions) {
  sessionList.innerHTML = "";
  if (!sessions.length) {
    const empty = document.createElement("div");
    empty.style.cssText = "padding:8px 14px;font-size:13px;color:var(--muted);";
    empty.textContent = "暂无历史会话";
    sessionList.appendChild(empty);
    return;
  }

  sessions.forEach(s => {
    const item = document.createElement("div");
    item.className = `session-item ${s.id === currentSessionId ? "active" : ""}`;
    item.innerHTML = `
      <button type="button" class="session-open-button">
        ${pageIcon()}
        <div class="session-item-body">
          <div class="session-item-title">${escapeHtml(s.title)}</div>
          <div class="session-item-meta">${formatDate(s.updatedAt)}</div>
        </div>
      </button>
      <div class="session-item-actions">
        <button type="button" class="session-action-button" data-action="rename">重命名</button>
        <button type="button" class="session-action-button danger" data-action="delete">删除</button>
      </div>`;
    item.querySelector(".session-open-button").addEventListener("click", async () => {
      saveCurrentSessionId(s.id);
      await loadSessionMessages();
      await loadSessions();
    });
    item.querySelector('[data-action="rename"]').addEventListener("click", async (event) => {
      event.stopPropagation();
      await renameSession(s.id, s.title);
    });
    item.querySelector('[data-action="delete"]').addEventListener("click", async (event) => {
      event.stopPropagation();
      await deleteSession(s.id);
    });
    sessionList.appendChild(item);
  });
}

async function renameSession(sessionId, currentTitle) {
  const nextTitle = window.prompt("请输入新的会话名称", currentTitle);
  if (!nextTitle || !nextTitle.trim()) return;

  const data = await authorizedCall(`/api/chat/sessions/${sessionId}`, {
    title: nextTitle.trim(),
  }, {
    method: "PATCH",
  });

  if (data?.code === 200) {
    await loadSessions();
  }
}

async function deleteSession(sessionId) {
  const confirmed = window.confirm("确定删除这段会话吗？删除后消息也会一起清空。");
  if (!confirmed) return;

  const data = await authorizedCall(`/api/chat/sessions/${sessionId}`, null, {
    method: "DELETE",
  });

  if (data?.code === 200) {
    if (currentSessionId === sessionId) {
      saveCurrentSessionId("");
      clearMessages();
      sessionMeta.textContent = "新建 Agent 会话模式";
    }
    await loadSessions();
  }
}

async function loadSessions() {
  if (!currentAgentId) return;
  const data = await authorizedCall(`/api/agents/${currentAgentId}/sessions`, null, { method: "GET" });
  if (!data || data.code !== 200 || !Array.isArray(data.data)) return;
  renderSessionList(data.data);
  const cur = data.data.find(s => s.id === currentSessionId);
  if (cur) sessionMeta.textContent = `当前：${cur.title}`;
  else sessionMeta.textContent = "新建 Agent 会话模式";
}

async function loadSessionMessages() {
  if (!currentAgentId || !currentSessionId) {
    clearMessages();
    sessionMeta.textContent = "新建 Agent 会话模式";
    return;
  }
  const data = await authorizedCall(`/api/agents/${currentAgentId}/sessions/${currentSessionId}/messages`, null, { method: "GET" });
  if (!data || data.code !== 200 || !Array.isArray(data.data)) return;
  messageList.innerHTML = "";
  data.data.forEach(m => appendMessage(m.role, m.content));
}

function setSendingState(sending) {
  isSending = sending;
  sendButton.disabled = sending;
  quickActionButtons.forEach(button => {
    button.disabled = sending;
  });
}

async function sendAgentMessage(text, sendingHint = "智能体正在处理…") {
  if (isSending) return;
  if (!text || !text.trim() || !currentAgentId) return;

  setSendingState(true);
  messageInput.value = "";

  appendMessage("user", text);
  appendTyping();
  sessionHint.textContent = sendingHint;

  const data = await authorizedCall(`/api/agents/${currentAgentId}/chat`, {
    sessionId: currentSessionId || null,
    message: text,
  });

  removeTyping();
  setSendingState(false);

  if (!data) return;
  if (data.code === 200 && data.data) {
    if (data.data.sessionId) saveCurrentSessionId(data.data.sessionId);
    await loadSessionMessages();
    modelHint.textContent = `模型：${data.data.model}`;
    sessionHint.textContent = "Agent 回复完成";
    await loadSessions();
  } else {
    appendMessage("assistant", `请求失败：${data.message || "未知错误"}`);
    sessionHint.textContent = "请求失败";
  }
}

chatForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const text = messageInput.value.trim();
  await sendAgentMessage(text);
});

quickActionButtons.forEach(button => {
  button.addEventListener("click", async () => {
    const action = button.dataset.quickAction;
    if (action !== "today-weather") return;

    const city = window.prompt("请输入要查询的城市", "上海");
    if (!city || !city.trim()) return;

    const prompt = `请调用天气工具查询${city.trim()}今天天气，并用 4 点告诉我：天气概况、温度范围、降水情况、穿衣建议。`;
    await sendAgentMessage(prompt, "智能体正在查询天气…");
  });
});

messageInput.addEventListener("keydown", e => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    chatForm.requestSubmit();
  }
});

document.getElementById("backWorkspaceButton").addEventListener("click", () => {
  window.location.href = "/workspace.html";
});

document.getElementById("logoutButton").addEventListener("click", () => {
  clearTokens();
  redirectToLogin();
});

document.getElementById("newAgentChatButton").addEventListener("click", async () => {
  saveCurrentSessionId("");
  clearMessages();
  sessionMeta.textContent = "新建 Agent 会话模式";
  await loadSessions();
});

async function init() {
  loadTokens();
  if (!accessToken && !refreshToken) { redirectToLogin(); return; }
  await loadAgent();
  await loadSessions();
  await loadSessionMessages();

  const pendingPrompt = localStorage.getItem(PENDING_AGENT_PROMPT_KEY);
  if (pendingPrompt && pendingPrompt.trim()) {
    localStorage.removeItem(PENDING_AGENT_PROMPT_KEY);
    saveCurrentSessionId("");
    clearMessages();
    sessionMeta.textContent = "新建 Agent 会话模式";
    await loadSessions();
    await sendAgentMessage(pendingPrompt, "智能体正在查询天气…");
  }
}

init();
