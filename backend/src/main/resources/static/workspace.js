/* ═══════════════════════════════════════════
   Storage keys
═══════════════════════════════════════════ */
const ACCESS_TOKEN_KEY     = "jagent.accessToken";
const REFRESH_TOKEN_KEY    = "jagent.refreshToken";
const SESSION_ID_KEY       = "jagent.currentSessionId";
const AGENT_ID_KEY         = "jagent.currentAgentId";
const PENDING_AGENT_PROMPT_KEY = "jagent.pendingAgentPrompt";
const COVER_VALUE_KEY      = "jagent.coverValue";    // cropped base64
const AVATAR_TYPE_KEY      = "jagent.avatarType";    // "color" | "image"
const AVATAR_VALUE_KEY     = "jagent.avatarValue";   // hex color or base64
const LIVE2D_COLLAPSED_KEY  = "jagent.workspaceLive2dCollapsed";
const WATERMARK_AGENT_ID   = "10000000-0000-0000-0000-000000000002";

let accessToken  = "";
let refreshToken = "";
let selectedKnowledgeBaseId = "";
let selectedAgentId = "";
let cachedAgents = [];
let live2dBootstrapped = false;
let coverCropState = null;

const AVATAR_COLORS = [
  "#d4736a","#6a9bd4","#6aad87",
  "#a06ad4","#d4a46a","#6ad4c8",
  "#e07070","#70a0e0","#70c090",
];

/* ═══════════════════════════════════════════
   Token helpers
═══════════════════════════════════════════ */
function loadTokens() {
  accessToken  = localStorage.getItem(ACCESS_TOKEN_KEY)  || "";
  refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || "";
}
function saveTokens(a, r) {
  accessToken  = a || "";
  refreshToken = r || "";
  accessToken  ? localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
               : localStorage.removeItem(ACCESS_TOKEN_KEY);
  refreshToken ? localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
               : localStorage.removeItem(REFRESH_TOKEN_KEY);
}
function clearTokens() {
  saveTokens("", "");
  localStorage.removeItem(SESSION_ID_KEY);
}
function redirectToLogin() { window.location.href = "/"; }
function getAgentSessionStorageKey(agentId) {
  return `jagent.agentSession.${agentId || "default"}`;
}
function openChat(sessionId) {
  sessionId ? localStorage.setItem(SESSION_ID_KEY, sessionId)
            : localStorage.removeItem(SESSION_ID_KEY);
  window.location.href = "/chat.html";
}
function openAgent(agentId, sessionId) {
  if (agentId)    localStorage.setItem(AGENT_ID_KEY, agentId);
  if (agentId) {
    const storageKey = getAgentSessionStorageKey(agentId);
    if (sessionId) localStorage.setItem(storageKey, sessionId);
    else localStorage.removeItem(storageKey);
  }
  window.location.href = "/agent-chat.html";
}
function openResearchAgent() {
  window.location.href = "/research-agent.html";
}

function openImageAgent() {
  window.location.href = "/image-agent.html";
}

function openWatermarkAgentChat(promptText = "") {
  localStorage.setItem(AGENT_ID_KEY, WATERMARK_AGENT_ID);
  localStorage.removeItem(getAgentSessionStorageKey(WATERMARK_AGENT_ID));
  if (promptText && promptText.trim()) {
    localStorage.setItem(PENDING_AGENT_PROMPT_KEY, promptText.trim());
  }
  window.location.href = "/agent-chat.html";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

/* ═══════════════════════════════════════════
   API helpers
═══════════════════════════════════════════ */
async function callApi(url, payload, options = {}) {
  const res = await fetch(url, {
    method:  options.method || "POST",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    body:    payload ? JSON.stringify(payload) : undefined,
  });
  return res.json();
}
async function refreshAccessToken() {
  if (!refreshToken) { clearTokens(); return null; }
  const data = await callApi("/api/auth/refresh", { refreshToken });
  if (data.code === 200 && data.data) { saveTokens(data.data.accessToken, data.data.refreshToken); return data; }
  clearTokens(); return null;
}
async function authorizedGet(url) {
  if (!accessToken) { redirectToLogin(); return null; }
  let data = await callApi(url, null, { method: "GET", headers: { Authorization: `Bearer ${accessToken}` } });
  if (data?.message?.toLowerCase().match(/jwt|token|expired/)) {
    const ok = await refreshAccessToken();
    if (!ok) { redirectToLogin(); return null; }
    data = await callApi(url, null, { method: "GET", headers: { Authorization: `Bearer ${accessToken}` } });
  }
  return data;
}
async function authorizedCall(url, payload, options = {}) {
  if (!accessToken) { redirectToLogin(); return null; }
  let data = await callApi(url, payload, { ...options, headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` } });
  if (data?.message?.toLowerCase().match(/jwt|token|expired/)) {
    const ok = await refreshAccessToken();
    if (!ok) { redirectToLogin(); return null; }
    data = await callApi(url, payload, { ...options, headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` } });
  }
  return data;
}

async function authorizedMultipart(url, formData, options = {}) {
  if (!accessToken) { redirectToLogin(); return null; }
  let res = await fetch(url, {
    method: options.method || "POST",
    headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` },
    body: formData,
  });
  let data = await res.json();
  if (data?.message?.toLowerCase().match(/jwt|token|expired/)) {
    const ok = await refreshAccessToken();
    if (!ok) { redirectToLogin(); return null; }
    res = await fetch(url, {
      method: options.method || "POST",
      headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` },
      body: formData,
    });
    data = await res.json();
  }
  return data;
}

async function loadTodayWeatherCard() {
  const data = await authorizedGet("/api/workspace/weather/today");
  if (!data) {
    return { errorMessage: "未拿到后端响应，请检查登录态或网络状态。" };
  }
  if (data.code !== 200 || !data.data) {
    return { errorMessage: data.message || "天气查询失败，请稍后再试。" };
  }
  return data.data;
}

async function loadTodayLolEsportsCard() {
  const data = await authorizedGet("/api/workspace/lol-esports/today");
  if (!data) {
    return { errorMessage: "未拿到后端响应，请检查登录态或网络状态。" };
  }
  if (data.code !== 200 || !data.data) {
    return { errorMessage: data.message || "赛事查询失败，请稍后再试。" };
  }
  return data.data;
}

async function loadNoteSyncCard() {
  const data = await authorizedGet("/api/workspace/notes/summary");
  if (!data) {
    return { statusText: "未拿到后端响应，请检查登录态或网络状态。" };
  }
  if (data.code !== 200 || !data.data) {
    return { statusText: data.message || "笔记目录扫描失败，请稍后再试。" };
  }
  return data.data;
}

async function syncNotesToNotion() {
  const data = await authorizedCall("/api/workspace/notes/sync", {});
  if (!data) {
    return { statusText: "同步失败，请检查网络或登录态。" };
  }
  if (data.code !== 200 || !data.data) {
    return { statusText: data.message || "同步失败，请稍后再试。" };
  }
  return data.data;
}

const LIVE2D_SCRIPT_CANDIDATES = [
  [
    "https://cdnjs.cloudflare.com/ajax/libs/pixi.js/6.2.0/browser/pixi.min.js",
    "https://cdn.jsdelivr.net/npm/pixi.js@6.2.0/dist/browser/pixi.min.js",
  ],
  [
    "https://fastly.jsdelivr.net/gh/stevenjoezhang/live2d-widget@latest/live2d.min.js",
    "https://unpkg.com/live2d-widget@3.1.4/lib/live2d.min.js",
  ],
  [
    "https://cdn.jsdelivr.net/npm/pixi-live2d-display/dist/cubism2.min.js",
    "https://unpkg.com/pixi-live2d-display/dist/cubism2.min.js",
  ],
];

function applyLive2dCollapsedState() {
  const shell = document.getElementById("live2dShell");
  if (!shell) return;
  const collapsed = localStorage.getItem(LIVE2D_COLLAPSED_KEY) !== "false";
  shell.classList.toggle("collapsed", collapsed);
}

function toggleLive2dShell() {
  const shell = document.getElementById("live2dShell");
  if (!shell) return;
  const nextCollapsed = !shell.classList.contains("collapsed");
  shell.classList.toggle("collapsed", nextCollapsed);
  localStorage.setItem(LIVE2D_COLLAPSED_KEY, nextCollapsed ? "true" : "false");
}

function loadExternalScript(src) {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${src}"]`)) {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = src;
    script.crossOrigin = "anonymous";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`failed to load ${src}`));
    document.head.appendChild(script);
  });
}

async function loadScriptWithFallback(candidates) {
  let lastError = null;
  for (const src of candidates) {
    try {
      await loadExternalScript(src);
      return src;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error("runtime unavailable");
}

async function initLive2dWidget() {
  if (live2dBootstrapped) return;
  live2dBootstrapped = true;

  const container = document.getElementById("live2dCanvas");
  if (!container) return;
  container.innerHTML = `<div class="live2d-placeholder">Live2D 模型准备中…</div>`;

  try {
    for (const candidates of LIVE2D_SCRIPT_CANDIDATES) {
      await loadScriptWithFallback(candidates);
    }

    const PIXI = window.PIXI;
    const Live2DModel = PIXI?.live2d?.Live2DModel;
    if (!PIXI || !Live2DModel) {
      throw new Error("Live2D runtime unavailable");
    }

    container.innerHTML = "";
    const canvas = document.createElement("canvas");
    canvas.style.width = "100%";
    canvas.style.height = "100%";
    canvas.style.display = "block";
    container.appendChild(canvas);

    const app = new PIXI.Application({
      view: canvas,
      width: container.clientWidth || 200,
      height: container.clientHeight || 220,
      backgroundAlpha: 0,
    });

    const model = await Live2DModel.from("/assets/live2d/miku.model.json");
    app.stage.addChild(model);
    model.anchor.set(0.5, 0.5);
    model.x = (container.clientWidth || 200) / 2;
    model.y = (container.clientHeight || 220) / 2 + 18;
    model.scale.set(0.22, 0.22);
  } catch (error) {
    console.error("Live2D init failed:", error);
    container.innerHTML = `
      <div class="live2d-error">
        <strong>Live2D 暂时不可用</strong>
        <span>这次主要是运行时脚本没有拉到，模型资源本地已经在。</span>
      </div>`;
  }
}

function renderWeatherPanel(weather) {
  const panel = document.getElementById("workspaceWeatherPanel");
  if (!panel) return;
  panel.style.display = "";

  if (!weather || weather.errorMessage) {
    panel.className = "wp-error panel-in";
    panel.innerHTML = `
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><circle cx="8" cy="8" r="6"/><path d="M8 5v3M8 10h.01"/></svg>
      ${weather?.errorMessage || "暂时无法获取天气信息，请稍后再试。"}`;
  } else {
    panel.className = "weather-panel panel-in";
    panel.innerHTML = `
      <div class="wp-top">
        <div class="wp-location">${weather.location}</div>
        <div class="wp-headline">${weather.headline}</div>
      </div>
      <div class="wp-chips">
        <div class="wp-chip"><div class="wp-chip-label">温度趋势</div><div class="wp-chip-val">${weather.trend}</div></div>
        <div class="wp-chip"><div class="wp-chip-label">雨具</div><div class="wp-chip-val">${weather.rainAdvice}</div></div>
        <div class="wp-chip"><div class="wp-chip-label">穿衣</div><div class="wp-chip-val">${weather.dressAdvice}</div></div>
      </div>
      ${weather.detail ? `<div class="wp-detail">${weather.detail}</div>` : ""}`;
    const desc = document.getElementById("weatherTriggerDesc");
    if (desc) desc.textContent = `${weather.location} · ${weather.headline}`;
  }
}

function renderNoteSyncPanel(payload) {
  const panel = document.getElementById("workspaceNoteSyncPanel");
  if (!panel) return;
  panel.style.display = "";

  const items = Array.isArray(payload?.items) ? payload.items : [];
  panel.className = "notes-panel panel-in";
  panel.innerHTML = `
    <div class="notes-panel-top">
      <div>
        <div class="notes-panel-title">本地笔记同步</div>
        <div class="notes-panel-meta">目录：${escapeHtml(payload?.rootPath || "-")}</div>
      </div>
      <div class="notes-panel-meta">${payload?.notionConfigured ? "Notion 已配置" : "Notion 未配置"}</div>
    </div>
    <div class="notes-note-item"><strong>${payload?.noteCount ?? 0}</strong> 篇 md，已绑定 <strong>${payload?.boundCount ?? 0}</strong> 篇，最近更新 ${escapeHtml(payload?.latestUpdatedAt || "暂无")}</div>
    <div class="notes-note-list">
      ${items.map(item => `
        <div class="notes-note-card">
          <div class="notes-note-title">${escapeHtml(item.title || item.fileName || "未命名笔记")}</div>
          <div class="notes-note-item">${escapeHtml(item.relativePath || "")}</div>
          <div class="notes-note-item">${item.notionPageId ? "已绑定 Notion 页面" : "尚未绑定 Notion 页面"} · ${escapeHtml(item.updatedAt || "")}</div>
        </div>
      `).join("") || `<div class="notes-note-card"><div class="notes-note-item">当前目录里还没有 markdown 文件。</div></div>`}
    </div>
    <div class="notes-panel-actions">
      <button id="notesSyncNowButton" class="btn-glass-light" type="button">一键同步到 Notion</button>
    </div>
    <div class="notes-panel-status">${escapeHtml(payload?.statusText || "")}</div>
  `;

  document.getElementById("notesSyncNowButton")?.addEventListener("click", async () => {
    const result = await syncNotesToNotion();
    renderNoteSyncPanel(result);
    document.getElementById("noteSyncTriggerDesc").textContent = result?.statusText || "同步完成";
  });
}

function renderLolPanel(report) {
  const panel = document.getElementById("workspaceLolPanel");
  if (!panel) return;
  panel.style.display = "";

  const dateStr = new Date().toLocaleDateString("zh-CN",{month:"short",day:"numeric"});

  if (!report || report.errorMessage) {
    panel.className = "lol-panel panel-in";
    panel.innerHTML = `
      <div class="lol-header">
        <span class="lol-badge">LoL Esports</span>
        <span class="lol-date">${dateStr}</span>
      </div>
      <div class="lol-empty">${report?.errorMessage || "暂时无法获取赛事信息，请稍后再试。"}</div>`;
    return;
  }

  const matches = Array.isArray(report.matches) ? report.matches : [];
  panel.className = "lol-panel panel-in";

  if (!matches.length) {
    panel.innerHTML = `
      <div class="lol-header">
        <span class="lol-badge">LoL Esports</span>
        <span class="lol-date">${report.dateLabel || dateStr}</span>
      </div>
      <div class="lol-empty">今日暂无 LCK 或官方国际赛事</div>`;
    return;
  }

  function matchRow(m) {
    const parts = (m.matchup || "").split(" vs ");
    const teamA = (parts[0] || "").trim();
    const teamB = (parts[1] || "").trim();
    const normalizedStatus = (m.status || "").trim();
    const isDone = normalizedStatus === "已结束" || normalizedStatus === "completed";
    const isLive = normalizedStatus === "进行中" || normalizedStatus === "live" || normalizedStatus === "inprogress";
    const statusKey = isDone ? "done" : isLive ? "live" : "soon";
    const statusLabel = isDone ? "已结束" : isLive ? "LIVE" : "待开始";
    const scoreHtml = m.scoreLine
      ? `<span class="lol-score-center">${m.scoreLine}</span>`
      : `<span class="lol-score-center lol-score-vs">vs</span>`;
    const metaLeft = [m.league, m.stage].filter(Boolean).join(" · ");
    return `
      <div class="lol-match">
        <div class="lol-match-top">
          <span class="lol-status lol-status-${statusKey}">${statusLabel}</span>
          <span class="lol-time">${m.timeLabel || ""}</span>
        </div>
        <div class="lol-matchup-row">
          <span class="lol-team-a">${teamA}</span>
          ${scoreHtml}
          <span class="lol-team-b">${teamB}</span>
        </div>
        <div class="lol-meta-row">
          <span class="lol-meta-left">${metaLeft}</span>
          <span class="lol-meta-right">${m.bestOfLabel || ""}</span>
        </div>
        ${m.recap ? `<div class="lol-recap">${m.recap}</div>` : ""}
      </div>`;
  }

  panel.innerHTML = `
    <div class="lol-header">
      <span class="lol-badge">LoL Esports</span>
      <span class="lol-date">${report.dateLabel || dateStr}</span>
      <span class="lol-title-txt">${report.title || ""}</span>
    </div>
    <div class="lol-matches">
      ${matches.map(matchRow).join("")}
    </div>`;
}

/* ═══════════════════════════════════════════
   Cover & Avatar persistence
═══════════════════════════════════════════ */
function applyCoverFromStorage() {
  const value = localStorage.getItem(COVER_VALUE_KEY);
  const el    = document.getElementById("coverBg");
  const removeBtn = document.getElementById("removeCoverBtn");
  if (!el) return;
  if (value && !value.startsWith("linear-gradient") && !value.startsWith("radial-gradient")) {
    el.style.backgroundColor = "rgba(255,255,255,0.08)";
    el.style.backgroundImage = `url(${value})`;
    el.style.backgroundSize  = "cover";
    el.style.backgroundPosition = "center";
    el.style.backgroundRepeat = "no-repeat";
    if (removeBtn) removeBtn.style.display = "inline-flex";
  } else {
    if (value && (value.startsWith("linear-gradient") || value.startsWith("radial-gradient"))) {
      localStorage.removeItem(COVER_VALUE_KEY);
    }
    el.style.backgroundImage = "";
    el.style.backgroundColor = "rgba(255,255,255,0.18)";
    if (removeBtn) removeBtn.style.display = "none";
  }
}

function applyAvatarFromStorage(defaultColor, defaultInitials) {
  const type  = localStorage.getItem(AVATAR_TYPE_KEY);
  const value = localStorage.getItem(AVATAR_VALUE_KEY);
  const el     = document.getElementById("pageAvatar");
  const txt    = document.getElementById("pageAvatarText");
  const sideEl = document.getElementById("userAvatar");
  const smEl   = document.getElementById("profileAvatarSm");

  if (!el) return;

  function applyImage(target, isDiv) {
    target.style.background = "";
    target.style.backgroundImage = `url(${value})`;
    target.style.backgroundSize  = "cover";
    target.style.backgroundPosition = "center";
    if (!isDiv) target.textContent = "";
  }
  function applyColor(target, color, label, isDiv) {
    target.style.backgroundImage = "";
    target.style.background = color;
    if (!isDiv) target.textContent = label;
  }

  if (type === "color" && value) {
    applyColor(el, value, defaultInitials, true);
    if (txt) { txt.textContent = defaultInitials; txt.style.display = ""; }
    if (sideEl) applyColor(sideEl, value, defaultInitials, false);
    if (smEl)   applyColor(smEl,   value, "", true);
  } else if (type === "image" && value) {
    applyImage(el, true);
    if (txt) txt.style.display = "none";
    if (sideEl) applyImage(sideEl, false);
    if (smEl)   applyImage(smEl, true);
  } else {
    applyColor(el, defaultColor, defaultInitials, true);
    if (txt) { txt.textContent = defaultInitials; txt.style.display = ""; }
    if (sideEl) applyColor(sideEl, defaultColor, defaultInitials, false);
    if (smEl)   applyColor(smEl, defaultColor, "", true);
  }
}

/* ═══════════════════════════════════════════
   Image resize utility
═══════════════════════════════════════════ */
function resizeImage(file, maxW, maxH, quality, cb) {
  const reader = new FileReader();
  reader.onload = e => {
    const img = new Image();
    img.onload = () => {
      const ratio = Math.min(maxW / img.width, maxH / img.height, 1);
      const w = Math.round(img.width  * ratio);
      const h = Math.round(img.height * ratio);
      const canvas = document.createElement("canvas");
      canvas.width = w; canvas.height = h;
      canvas.getContext("2d").drawImage(img, 0, 0, w, h);
      cb(canvas.toDataURL("image/jpeg", quality));
    };
    img.src = e.target.result;
  };
  reader.readAsDataURL(file);
}

/* ═══════════════════════════════════════════
   Cover cropper
═══════════════════════════════════════════ */
function clamp(num, min, max) {
  return Math.min(Math.max(num, min), max);
}

function getCoverCropEls() {
  return {
    modal: document.getElementById("coverCropModal"),
    frame: document.getElementById("coverCropFrame"),
    image: document.getElementById("coverCropImage"),
    zoom: document.getElementById("coverCropZoomInput"),
  };
}

function syncCoverCropImage() {
  const { image, frame, zoom } = getCoverCropEls();
  if (!coverCropState || !image || !frame || !zoom) return;
  const frameRect = frame.getBoundingClientRect();
  const frameW = frameRect.width;
  const frameH = frameRect.height;
  const displayW = coverCropState.width * coverCropState.scale;
  const displayH = coverCropState.height * coverCropState.scale;
  const maxX = Math.max(0, (displayW - frameW) / 2);
  const maxY = Math.max(0, (displayH - frameH) / 2);

  coverCropState.x = clamp(coverCropState.x, -maxX, maxX);
  coverCropState.y = clamp(coverCropState.y, -maxY, maxY);

  image.style.width = `${coverCropState.width}px`;
  image.style.height = `${coverCropState.height}px`;
  image.style.transform = `translate(-50%, -50%) translate(${coverCropState.x}px, ${coverCropState.y}px) scale(${coverCropState.scale})`;

  const zoomPercent = Math.round((coverCropState.scale / coverCropState.minScale) * 100);
  zoom.value = String(clamp(zoomPercent, 100, 220));
}

function openCoverCropModal(dataUrl, width, height) {
  const { modal, frame, image, zoom } = getCoverCropEls();
  if (!modal || !frame || !image || !zoom) return;
  modal.style.display = "flex";

  requestAnimationFrame(() => {
    const frameRect = frame.getBoundingClientRect();
    const minScale = Math.max(frameRect.width / width, frameRect.height / height);
    coverCropState = {
      src: dataUrl,
      width,
      height,
      scale: minScale,
      minScale,
      x: 0,
      y: 0,
      dragging: false,
      startX: 0,
      startY: 0,
      originX: 0,
      originY: 0,
    };
    image.src = dataUrl;
    syncCoverCropImage();
  });
}

function closeCoverCropModal() {
  const { modal, frame } = getCoverCropEls();
  if (modal) modal.style.display = "none";
  if (frame) frame.classList.remove("dragging");
  coverCropState = null;
}

function finalizeCoverCrop() {
  const { frame } = getCoverCropEls();
  if (!coverCropState || !frame) return;
  const frameRect = frame.getBoundingClientRect();
  const frameW = frameRect.width;
  const frameH = frameRect.height;
  const displayW = coverCropState.width * coverCropState.scale;
  const displayH = coverCropState.height * coverCropState.scale;
  const imageLeft = frameW / 2 - displayW / 2 + coverCropState.x;
  const imageTop = frameH / 2 - displayH / 2 + coverCropState.y;
  const sx = Math.max(0, (0 - imageLeft) / coverCropState.scale);
  const sy = Math.max(0, (0 - imageTop) / coverCropState.scale);
  const sWidth = Math.min(coverCropState.width, frameW / coverCropState.scale);
  const sHeight = Math.min(coverCropState.height, frameH / coverCropState.scale);

  const canvas = document.createElement("canvas");
  canvas.width = 1600;
  canvas.height = 480;
  const ctx = canvas.getContext("2d");
  const img = new Image();
  img.onload = () => {
    ctx.drawImage(img, sx, sy, sWidth, sHeight, 0, 0, canvas.width, canvas.height);
    localStorage.setItem(COVER_VALUE_KEY, canvas.toDataURL("image/jpeg", 0.9));
    applyCoverFromStorage();
    closeCoverCropModal();
  };
  img.src = coverCropState.src;
}

document.getElementById("changeCoverBtn").addEventListener("click", () => {
  document.getElementById("coverUploadInput").click();
});

document.getElementById("removeCoverBtn").addEventListener("click", () => {
  localStorage.removeItem(COVER_VALUE_KEY);
  applyCoverFromStorage();
});

document.getElementById("closeCoverCropModal").addEventListener("click", closeCoverCropModal);
document.getElementById("coverCropModal").addEventListener("click", event => {
  if (event.target === document.getElementById("coverCropModal")) {
    closeCoverCropModal();
  }
});

document.getElementById("coverUploadInput").addEventListener("change", event => {
  const file = event.target.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = e => {
    const img = new Image();
    img.onload = () => openCoverCropModal(e.target.result, img.width, img.height);
    img.src = e.target.result;
  };
  reader.readAsDataURL(file);
  event.target.value = "";
});

document.getElementById("coverCropZoomInput").addEventListener("input", event => {
  if (!coverCropState) return;
  const percent = Number(event.target.value) / 100;
  coverCropState.scale = coverCropState.minScale * percent;
  syncCoverCropImage();
});

document.getElementById("confirmCoverCropBtn").addEventListener("click", finalizeCoverCrop);

document.getElementById("coverCropFrame").addEventListener("pointerdown", event => {
  if (!coverCropState) return;
  coverCropState.dragging = true;
  coverCropState.startX = event.clientX;
  coverCropState.startY = event.clientY;
  coverCropState.originX = coverCropState.x;
  coverCropState.originY = coverCropState.y;
  event.currentTarget.classList.add("dragging");
});

window.addEventListener("pointermove", event => {
  if (!coverCropState?.dragging) return;
  coverCropState.x = coverCropState.originX + (event.clientX - coverCropState.startX);
  coverCropState.y = coverCropState.originY + (event.clientY - coverCropState.startY);
  syncCoverCropImage();
});

window.addEventListener("pointerup", () => {
  const { frame } = getCoverCropEls();
  if (frame) frame.classList.remove("dragging");
  if (coverCropState) coverCropState.dragging = false;
});

/* ═══════════════════════════════════════════
   Avatar picker
═══════════════════════════════════════════ */
let _defaultAvatarColor = "#555";
let _defaultAvatarInitials = "?";

function buildAvatarColors() {
  const grid = document.getElementById("avatarColorGrid");
  grid.innerHTML = "";
  const currentVal = localStorage.getItem(AVATAR_VALUE_KEY);
  AVATAR_COLORS.forEach(color => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "color-swatch" + (color === currentVal ? " selected" : "");
    btn.style.background = color;
    btn.title = color;
    btn.addEventListener("click", () => {
      localStorage.setItem(AVATAR_TYPE_KEY, "color");
      localStorage.setItem(AVATAR_VALUE_KEY, color);
      applyAvatarFromStorage(_defaultAvatarColor, _defaultAvatarInitials);
      grid.querySelectorAll(".color-swatch").forEach(b => b.classList.remove("selected"));
      btn.classList.add("selected");
    });
    grid.appendChild(btn);
  });
}

document.getElementById("pageAvatar").addEventListener("click", e => {
  e.stopPropagation();
  const popover = document.getElementById("avatarPickerPopover");
  const rect = document.getElementById("pageAvatar").getBoundingClientRect();
  popover.style.top  = (rect.bottom + 8) + "px";
  popover.style.left = rect.left + "px";
  buildAvatarColors();
  popover.style.display = popover.style.display === "block" ? "none" : "block";
});
document.getElementById("avatarUploadInput").addEventListener("change", e => {
  const file = e.target.files[0];
  if (!file) return;
  resizeImage(file, 300, 300, 0.88, dataUrl => {
    localStorage.setItem(AVATAR_TYPE_KEY, "image");
    localStorage.setItem(AVATAR_VALUE_KEY, dataUrl);
    applyAvatarFromStorage(_defaultAvatarColor, _defaultAvatarInitials);
    document.getElementById("avatarPickerPopover").style.display = "none";
  });
  e.target.value = "";
});
document.getElementById("resetAvatarBtn").addEventListener("click", () => {
  localStorage.removeItem(AVATAR_TYPE_KEY);
  localStorage.removeItem(AVATAR_VALUE_KEY);
  applyAvatarFromStorage(_defaultAvatarColor, _defaultAvatarInitials);
  document.getElementById("avatarPickerPopover").style.display = "none";
});

/* Global click: close popover + dropdowns */
document.addEventListener("click", e => {
  if (!e.target.closest("#pageAvatar") && !e.target.closest("#avatarPickerPopover")) {
    document.getElementById("avatarPickerPopover").style.display = "none";
  }
  if (!e.target.closest(".item-menu-wrap") && !e.target.closest(".session-row-menu-wrap")) {
    document.querySelectorAll(".item-dropdown.open").forEach(m => m.classList.remove("open"));
  }
});

/* ═══════════════════════════════════════════
   Dropdown menu builder (reusable)
═══════════════════════════════════════════ */
function dotsIcon() {
  return `<svg viewBox="0 0 16 16" fill="currentColor">
    <circle cx="3"  cy="8" r="1.3"/>
    <circle cx="8"  cy="8" r="1.3"/>
    <circle cx="13" cy="8" r="1.3"/>
  </svg>`;
}

function makeDropdown(onRename, onDelete) {
  const wrap = document.createElement("div");
  wrap.className = "item-menu-wrap";

  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "item-menu-btn";
  btn.setAttribute("aria-label", "更多操作");
  btn.innerHTML = dotsIcon();

  const menu = document.createElement("div");
  menu.className = "item-dropdown";
  menu.innerHTML = `
    <button type="button">重命名</button>
    <div class="drop-sep"></div>
    <button type="button" class="danger">删除</button>`;

  btn.addEventListener("click", e => {
    e.stopPropagation();
    const isOpen = menu.classList.contains("open");
    document.querySelectorAll(".item-dropdown.open").forEach(m => m.classList.remove("open"));
    if (!isOpen) menu.classList.add("open");
  });
  menu.children[0].addEventListener("click", e => { e.stopPropagation(); menu.classList.remove("open"); onRename(); });
  menu.children[2].addEventListener("click", e => { e.stopPropagation(); menu.classList.remove("open"); onDelete(); });

  wrap.appendChild(btn);
  wrap.appendChild(menu);
  return wrap;
}

/* Row-level dropdown (same logic, different class names for CSS) */
function makeRowDropdown(onRename, onDelete) {
  const wrap = document.createElement("div");
  wrap.className = "session-row-menu-wrap item-menu-wrap";

  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "session-row-menu-btn item-menu-btn";
  btn.setAttribute("aria-label", "更多操作");
  btn.innerHTML = dotsIcon();

  const menu = document.createElement("div");
  menu.className = "item-dropdown";
  menu.innerHTML = `
    <button type="button">重命名</button>
    <div class="drop-sep"></div>
    <button type="button" class="danger">删除</button>`;

  btn.addEventListener("click", e => {
    e.stopPropagation();
    const isOpen = menu.classList.contains("open");
    document.querySelectorAll(".item-dropdown.open").forEach(m => m.classList.remove("open"));
    if (!isOpen) menu.classList.add("open");
  });
  menu.children[0].addEventListener("click", e => { e.stopPropagation(); menu.classList.remove("open"); onRename(); });
  menu.children[2].addEventListener("click", e => { e.stopPropagation(); menu.classList.remove("open"); onDelete(); });

  wrap.appendChild(btn);
  wrap.appendChild(menu);
  return wrap;
}

/* ═══════════════════════════════════════════
   Greeting & helpers
═══════════════════════════════════════════ */
function setGreeting(displayName) {
  const h = new Date().getHours();
  const g = h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
  document.getElementById("welcomeTitle").innerHTML = `${g}, <span class="script-name">${escapeHtml(displayName)}</span>`;
  document.getElementById("greetingDate").textContent = new Date().toLocaleDateString("zh-CN",
    { year: "numeric", month: "long", day: "numeric", weekday: "long" });
}
function avatarDefaultColor(name) {
  const p = ["#d4736a","#6a9bd4","#6aad87","#a06ad4","#d4a46a","#6ad4c8"];
  return p[(name || "").charCodeAt(0) % p.length];
}
function initials(name) {
  if (!name) return "?";
  return name.trim().split(/\s+/).map(w => w[0]).join("").slice(0,2).toUpperCase();
}
function chatIcon() {
  return `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
    <path d="M14 2H2a1 1 0 00-1 1v8a1 1 0 001 1h3l3 3 3-3h3a1 1 0 001-1V3a1 1 0 00-1-1z"/>
  </svg>`;
}
function agentIcon() {
  return `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
    <rect x="2" y="3" width="12" height="10" rx="2"/>
    <path d="M6 7h.01M10 7h.01M6 10h4"/>
  </svg>`;
}
function arrowIcon() {
  return `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
    <path d="M6 3l5 5-5 5"/>
  </svg>`;
}
function formatDate(iso) {
  return new Date(iso).toLocaleString("zh-CN",
    { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

/* ═══════════════════════════════════════════
   Session CRUD
═══════════════════════════════════════════ */
async function renameSession(sessionId, currentTitle) {
  const next = window.prompt("请输入新的会话名称", currentTitle);
  if (!next?.trim()) return;
  const data = await authorizedCall(`/api/chat/sessions/${sessionId}`, { title: next.trim() }, { method: "PATCH" });
  if (data?.code === 200) await loadWorkspace();
}
async function deleteSession(sessionId) {
  if (!window.confirm("确定删除这段会话吗？")) return;
  const data = await authorizedCall(`/api/chat/sessions/${sessionId}`, null, { method: "DELETE" });
  if (data?.code === 200) {
    if (localStorage.getItem(SESSION_ID_KEY) === sessionId) localStorage.removeItem(SESSION_ID_KEY);
    await loadWorkspace();
  }
}

/* ═══════════════════════════════════════════
   Render functions
═══════════════════════════════════════════ */
function renderSidebarSessions(sessions) {
  const el = document.getElementById("sidebarSessionList");
  el.innerHTML = "";
  sessions.forEach(s => {
    const item = document.createElement("div");
    item.className = "sidebar-item";

    const openBtn = document.createElement("button");
    openBtn.type = "button";
    openBtn.className = "sidebar-item-open";
    openBtn.innerHTML = `${chatIcon()}<span>${s.title}</span>`;
    openBtn.addEventListener("click", () => openChat(s.id));

    item.appendChild(openBtn);
    item.appendChild(makeDropdown(
      () => renameSession(s.id, s.title),
      () => deleteSession(s.id)
    ));
    el.appendChild(item);
  });
}

function renderSidebarAgents(agents) {
  const el = document.getElementById("sidebarAgentList");
  el.innerHTML = "";
  if (!agents.length) {
    const p = document.createElement("div");
    p.style.cssText = "padding:4px 6px;font-size:12px;color:var(--muted);";
    p.textContent = "暂无可用智能体";
    el.appendChild(p);
    return;
  }
  agents.forEach(a => {
    const item = document.createElement("div");
    item.className = "sidebar-item";
    const openBtn = document.createElement("button");
    openBtn.type = "button";
    openBtn.className = "sidebar-item-open";
    openBtn.innerHTML = `${agentIcon()}<span>${a.name}</span>`;
    openBtn.addEventListener("click", () => {
      selectedAgentId = a.id;
      localStorage.setItem(AGENT_ID_KEY, a.id);
      updateSelectedAgentUI(cachedAgents);
      openAgent(a.id, "");
    });
    item.appendChild(openBtn);
    el.appendChild(item);
  });
}

function renderMainSessions(sessions) {
  const el = document.getElementById("mainSessionList");
  if (!sessions.length) {
    el.innerHTML = `<div class="empty-state">
      <div class="empty-state-icon">${chatIcon()}</div>
      <p>还没有会话记录<br>点击 New Chat 开始对话</p>
    </div>`;
    return;
  }
  el.innerHTML = "";
  sessions.forEach(s => {
    const row = document.createElement("div");
    row.className = "session-row";

    const openBtn = document.createElement("button");
    openBtn.type = "button";
    openBtn.className = "session-row-open";
    openBtn.innerHTML = `
      <div class="session-row-icon">${chatIcon()}</div>
      <div class="session-row-body">
        <div class="session-row-title">${s.title}</div>
        <div class="session-row-meta">最近更新 ${formatDate(s.updatedAt)}</div>
      </div>
      <div class="session-row-arrow">${arrowIcon()}</div>`;
    openBtn.addEventListener("click", () => openChat(s.id));

    row.appendChild(openBtn);
    row.appendChild(makeRowDropdown(
      () => renameSession(s.id, s.title),
      () => deleteSession(s.id)
    ));
    el.appendChild(row);
  });
}

function getAgentOverride(id)        { return { icon: localStorage.getItem(`jagent.a.${id}.icon`), name: localStorage.getItem(`jagent.a.${id}.name`) }; }
function setAgentOverride(id, k, v)  { v ? localStorage.setItem(`jagent.a.${id}.${k}`, v) : localStorage.removeItem(`jagent.a.${id}.${k}`); }

function renderAgentModules(agents) {
  const el = document.getElementById("agentModuleList");
  if (!el) return;
  if (!agents.length) {
    el.innerHTML = `<div class="empty-state">
      <div class="empty-state-icon">${agentIcon()}</div>
      <p>当前还没有可用智能体</p>
    </div>`;
    return;
  }
  el.innerHTML = "";
  agents.forEach((a, i) => {
    const ov = getAgentOverride(a.id);
    const displayIcon = ov.icon || (a.name || "A").replace(/\s+/g, "").slice(0, 2).toUpperCase();
    const displayName = ov.name || a.name;

    const card = document.createElement("div");
    card.className = "agent-card";
    card.style.animationDelay = `${i * 0.06}s`;

    const kbsHtml = a.knowledgeEnabled && a.allowedKnowledgeBaseNames?.length
      ? a.allowedKnowledgeBaseNames.map(n => `<span class="agent-kb-tag">${n}</span>`).join("")
      : `<span class="agent-kb-none">未绑定知识库</span>`;

    card.innerHTML = `
      <button class="agent-card-gear" type="button" title="知识库配置">
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"
             stroke-linecap="round" stroke-linejoin="round">
          <circle cx="8" cy="8" r="2.2"/>
          <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2
                   M3.6 3.6l1.4 1.4M11 11l1.4 1.4
                   M3.6 12.4l1.4-1.4M11 5l1.4-1.4"/>
        </svg>
      </button>
      <div class="agent-card-avatar">${displayIcon}</div>
      <div class="agent-card-name">${displayName}</div>
      <div class="agent-card-kbs">${kbsHtml}</div>`;

    /* click whole card → open agent */
    card.addEventListener("click", () => {
      selectedAgentId = a.id;
      localStorage.setItem(AGENT_ID_KEY, a.id);
      openAgent(a.id, "");
    });

    /* avatar click → edit icon */
    const avatarEl = card.querySelector(".agent-card-avatar");
    avatarEl.addEventListener("click", e => {
      e.stopPropagation();
      const cur = getAgentOverride(a.id).icon || displayIcon;
      const val = window.prompt("输入图标（emoji 或 2 个字符）", cur);
      if (val === null) return;
      const trimmed = [...val.trim()].slice(0, 2).join("") || displayIcon;
      setAgentOverride(a.id, "icon", trimmed);
      avatarEl.textContent = trimmed;
    });

    /* name click → inline edit */
    const nameEl = card.querySelector(".agent-card-name");
    nameEl.addEventListener("click", e => {
      e.stopPropagation();
      const input = document.createElement("input");
      input.className = "agent-card-name-input";
      input.value = nameEl.textContent;
      nameEl.replaceWith(input);
      input.focus(); input.select();
      const save = () => {
        const v = input.value.trim() || a.name;
        setAgentOverride(a.id, "name", v === a.name ? "" : v);
        input.replaceWith(nameEl);
        nameEl.textContent = v;
      };
      input.addEventListener("blur", save);
      input.addEventListener("keydown", e2 => {
        if (e2.key === "Enter")  input.blur();
        if (e2.key === "Escape") { input.replaceWith(nameEl); }
      });
    });

    /* gear → KB modal */
    card.querySelector(".agent-card-gear").addEventListener("click", e => {
      e.stopPropagation();
      openKbModal(a);
    });

    el.appendChild(card);
  });
}

let _kbModalAgent = null;
function openKbModal(agent) {
  _kbModalAgent = agent;
  selectedAgentId = agent.id;
  localStorage.setItem(AGENT_ID_KEY, agent.id);
  const modal = document.getElementById("kbManageModal");
  const title = document.getElementById("kbModalTitle");
  if (title) title.textContent = `${agent.name} · 知识库配置`;
  if (modal) modal.style.display = "flex";
  updateSelectedAgentUI(cachedAgents);
  refreshKnowledgeBases();
}
function closeKbModal() {
  const modal = document.getElementById("kbManageModal");
  if (modal) modal.style.display = "none";
}

function ensureSelectedAgent(agents) {
  if (!agents.length) {
    selectedAgentId = "";
    localStorage.removeItem(AGENT_ID_KEY);
    return null;
  }
  const storedAgentId = localStorage.getItem(AGENT_ID_KEY) || "";
  if (storedAgentId && agents.some(agent => agent.id === storedAgentId)) {
    selectedAgentId = storedAgentId;
  }
  if (!selectedAgentId || !agents.some(agent => agent.id === selectedAgentId)) {
    selectedAgentId = agents[0].id;
  }
  localStorage.setItem(AGENT_ID_KEY, selectedAgentId);
  return agents.find(agent => agent.id === selectedAgentId) || agents[0];
}

function updateSelectedAgentUI(agents) {
  const selectedAgent = ensureSelectedAgent(agents);
  const bindingHint   = document.getElementById("knowledgeBindingHint");
  const agentCardTitle = document.getElementById("agentCardTitle");
  const openAgentBtn   = document.getElementById("openAgentButtonInline");
  const navAgentBtn    = document.getElementById("navAgentButton");

  if (!selectedAgent) {
    if (agentCardTitle) agentCardTitle.textContent = "暂无智能体";
    if (openAgentBtn)  openAgentBtn.disabled = true;
    if (bindingHint)   bindingHint.textContent = "请选择知识库和智能体后进行绑定";
    return;
  }

  const displayName = getAgentOverride(selectedAgent.id).name || selectedAgent.name;
  if (agentCardTitle) agentCardTitle.textContent = displayName;
  if (openAgentBtn) {
    openAgentBtn.disabled = false;
    openAgentBtn.onclick = () => openAgent(selectedAgent.id, "");
  }
  if (navAgentBtn) navAgentBtn.onclick = () => openAgent(selectedAgent.id, "");

  if (bindingHint) {
    const selectedKbText = selectedKnowledgeBaseId ? "已选中知识库，可执行绑定" : "请先选中知识库";
    bindingHint.textContent = `当前绑定目标：${displayName} · ${selectedKbText}`;
  }
}

async function bindSelectedKnowledgeBaseToAgent() {
  const selectedAgent = cachedAgents.find(agent => agent.id === selectedAgentId);
  if (!selectedAgent) {
    window.alert("请先选择一个智能体");
    return;
  }
  if (!selectedKnowledgeBaseId) {
    window.alert("请先选择一个知识库");
    return;
  }

  const nextIds = Array.from(new Set([...(selectedAgent.allowedKnowledgeBaseIds || []), selectedKnowledgeBaseId]));
  const data = await authorizedCall(`/api/agents/${selectedAgent.id}/knowledge-bases`, {
    knowledgeBaseIds: nextIds,
  }, { method: "PATCH" });

  if (data?.code === 200 && data.data) {
    cachedAgents = cachedAgents.map(agent => agent.id === data.data.id ? data.data : agent);
    renderSidebarAgents(cachedAgents);
    renderAgentModules(cachedAgents);
    updateSelectedAgentUI(cachedAgents);
  } else {
    window.alert(data?.message || "绑定知识库失败");
  }
}

async function loadKnowledgeBases() {
  const data = await authorizedGet("/api/knowledge-bases");
  if (!data || data.code !== 200 || !Array.isArray(data.data)) return [];
  return data.data;
}

async function loadKnowledgeDocuments(knowledgeBaseId) {
  const data = await authorizedGet(`/api/knowledge-bases/${knowledgeBaseId}/documents`);
  if (!data || data.code !== 200 || !Array.isArray(data.data)) return [];
  return data.data;
}

function renderKnowledgeBases(knowledgeBases) {
  const el = document.getElementById("knowledgeBaseList");
  if (!el) return;

  if (!knowledgeBases.length) {
    selectedKnowledgeBaseId = "";
    el.innerHTML = `<div class="empty-state"><p>暂无知识库</p></div>`;
    renderKnowledgeDocuments([]);
    return;
  }

  if (!selectedKnowledgeBaseId || !knowledgeBases.some(kb => kb.id === selectedKnowledgeBaseId)) {
    selectedKnowledgeBaseId = knowledgeBases[0].id;
  }

  el.innerHTML = "";
  knowledgeBases.forEach(kb => {
    const row = document.createElement("div");
    row.className = `session-row ${kb.id === selectedKnowledgeBaseId ? "selected" : ""}`;
    const openBtn = document.createElement("button");
    openBtn.type = "button";
    openBtn.className = "session-row-open";
    openBtn.innerHTML = `
      <div class="session-row-icon">${chatIcon()}</div>
      <div class="session-row-body">
        <div class="session-row-title">${kb.name}${kb.id === selectedKnowledgeBaseId ? " · 当前" : ""}</div>
        <div class="session-row-meta">${kb.description || "知识库容器"}</div>
      </div>
      <div class="session-row-arrow">${arrowIcon()}</div>`;
    openBtn.addEventListener("click", async () => {
      selectedKnowledgeBaseId = kb.id;
      renderKnowledgeBases(knowledgeBases);
      updateSelectedAgentUI(cachedAgents);
      await refreshKnowledgeDocuments();
    });
    row.appendChild(openBtn);
    el.appendChild(row);
  });
}

function renderKnowledgeDocuments(documents) {
  const el = document.getElementById("knowledgeDocumentList");
  if (!el) return;

  if (!selectedKnowledgeBaseId) {
    el.innerHTML = "";
    return;
  }

  if (!documents.length) {
    el.innerHTML = `<div class="empty-state"><p>当前知识库暂无文档</p></div>`;
    return;
  }

  el.innerHTML = "";
  documents.forEach(doc => {
    const row = document.createElement("div");
    row.className = "session-row";
    row.innerHTML = `
      <button type="button" class="session-row-open">
        <div class="session-row-icon">${chatIcon()}</div>
        <div class="session-row-body">
          <div class="session-row-title">${doc.filename}</div>
          <div class="session-row-meta">${doc.filetype || "unknown"} · ${doc.size ?? 0} bytes</div>
        </div>
      </button>`;
    el.appendChild(row);
  });
}

function renderMainKnowledgeBases(knowledgeBases) {
  const el = document.getElementById("mainKbList");
  if (!el) return;
  if (!knowledgeBases.length) {
    el.innerHTML = `<div class="empty-state"><p>暂无知识库，点击上方新建</p></div>`;
    return;
  }
  el.innerHTML = "";
  knowledgeBases.forEach(kb => {
    const card = document.createElement("div");
    card.className = `kb-card${kb.id === selectedKnowledgeBaseId ? " selected" : ""}`;
    card.innerHTML = `
      <div class="kb-card-icon">📖</div>
      <div class="kb-card-name">${kb.name}</div>
      <div class="kb-card-meta">${kb.description || "知识库容器"}</div>`;
    card.addEventListener("click", () => {
      selectedKnowledgeBaseId = kb.id;
      renderMainKnowledgeBases(knowledgeBases);
    });
    el.appendChild(card);
  });
}

async function refreshKnowledgeBases() {
  const knowledgeBases = await loadKnowledgeBases();
  renderKnowledgeBases(knowledgeBases);
  renderMainKnowledgeBases(knowledgeBases);
  await refreshKnowledgeDocuments();
}

async function refreshKnowledgeDocuments() {
  if (!selectedKnowledgeBaseId) {
    renderKnowledgeDocuments([]);
    return;
  }
  const documents = await loadKnowledgeDocuments(selectedKnowledgeBaseId);
  renderKnowledgeDocuments(documents);
}

async function createAgent() {
  const name = window.prompt("智能体名称", "新智能体");
  if (!name || !name.trim()) return;
  const description = window.prompt("智能体简介（可留空）", "");
  const data = await authorizedCall("/api/agents", {
    name: name.trim(),
    description: description?.trim() || null,
    systemPrompt: null,
    modelName: null,
    knowledgeEnabled: false,
    allowedKnowledgeBaseIds: [],
  });
  if (data?.code === 200 && data.data) {
    cachedAgents = [...cachedAgents, data.data];
    renderSidebarAgents(cachedAgents);
    renderAgentModules(cachedAgents);
    updateSelectedAgentUI(cachedAgents);
  } else {
    window.alert(data?.message || "创建智能体失败");
  }
}

async function createKnowledgeBase() {
  const name = window.prompt("请输入知识库名称", "eshop 设计库");
  if (!name || !name.trim()) return;
  const description = window.prompt("请输入知识库简介", "用于存放数据库设计与规格文档");
  const data = await authorizedCall("/api/knowledge-bases", {
    name: name.trim(),
    description: description ? description.trim() : null,
    metadata: null,
  });
  if (data?.code === 200) {
    await refreshKnowledgeBases();
  } else {
    window.alert(data?.message || "创建知识库失败");
  }
}

async function uploadKnowledgeDocument(file) {
  if (!selectedKnowledgeBaseId) {
    window.alert("请先创建并选中一个知识库");
    return;
  }
  if (!file) return;

  const formData = new FormData();
  formData.append("file", file);
  const data = await authorizedMultipart(`/api/knowledge-bases/${selectedKnowledgeBaseId}/documents/upload`, formData);
  if (data?.code === 200) {
    await refreshKnowledgeDocuments();
  } else {
    window.alert(data?.message || "上传文档失败");
  }
}

/* ═══════════════════════════════════════════
   Main load
═══════════════════════════════════════════ */
async function loadWorkspace() {
  loadTokens();
  if (!accessToken && !refreshToken) { redirectToLogin(); return; }

  const profileData = await authorizedGet("/api/auth/me");
  if (!profileData || profileData.code !== 200) { redirectToLogin(); return; }

  const { userId, displayName, email } = profileData.data;

  setGreeting(displayName);
  document.getElementById("welcomeSubtitle").textContent = email;

  /* Sidebar footer */
  const unEl = document.getElementById("userName");
  const ueEl = document.getElementById("userEmail");
  if (unEl) unEl.textContent = displayName;
  if (ueEl) ueEl.textContent = email;

  /* Set default avatar (may be overridden by localStorage below) */
  _defaultAvatarColor    = avatarDefaultColor(displayName);
  _defaultAvatarInitials = initials(displayName);
  applyAvatarFromStorage(_defaultAvatarColor, _defaultAvatarInitials);

  /* Apply stored cover */
  applyCoverFromStorage();

  /* Profile card */
  document.getElementById("statUserId").textContent      = userId.slice(0, 8) + "…";
  document.getElementById("statDisplayName").textContent = displayName;
  document.getElementById("statEmail").textContent       = email;

  /* Sessions */
  const sessData = await authorizedGet("/api/chat/sessions");
  if (sessData?.code === 200 && Array.isArray(sessData.data)) {
    document.getElementById("statSessions").textContent = sessData.data.length + " 条";
    renderSidebarSessions(sessData.data);
    renderMainSessions(sessData.data);
  }

  /* Agents */
  const agentData = await authorizedGet("/api/agents");
  if (agentData?.code === 200 && Array.isArray(agentData.data) && agentData.data.length) {
    cachedAgents = agentData.data;
    renderSidebarAgents(cachedAgents);
    renderAgentModules(cachedAgents);
    updateSelectedAgentUI(cachedAgents);
  } else {
    cachedAgents = [];
    renderSidebarAgents([]);
    renderAgentModules([]);
    updateSelectedAgentUI([]);
  }

  /* Knowledge Bases (main page module) */
  const kbData = await authorizedGet("/api/knowledge-bases");
  if (kbData?.code === 200 && Array.isArray(kbData.data)) {
    renderMainKnowledgeBases(kbData.data);
  }

  const weatherButton = document.getElementById("workspaceWeatherButton");
  if (weatherButton) {
    weatherButton.onclick = async () => {
      weatherButton.classList.add("loading");
      weatherButton.disabled = true;
      try {
        const weather = await loadTodayWeatherCard();
        renderWeatherPanel(weather);
      } finally {
        weatherButton.classList.remove("loading");
        weatherButton.disabled = false;
      }
    };
  }

  const lolButton = document.getElementById("workspaceLolButton");
  if (lolButton) {
    lolButton.onclick = async () => {
      lolButton.classList.add("loading");
      lolButton.disabled = true;
      try {
        const report = await loadTodayLolEsportsCard();
        renderLolPanel(report);
        const desc = document.getElementById("lolTriggerDesc");
        if (desc && report && !report.errorMessage) {
          const cnt = Array.isArray(report.matches) ? report.matches.length : 0;
          desc.textContent = cnt ? `今日 ${cnt} 场赛事` : "今日暂无赛事";
        }
      } finally {
        lolButton.classList.remove("loading");
        lolButton.disabled = false;
      }
    };
  }

  const noteSyncButton = document.getElementById("workspaceNoteSyncButton");
  if (noteSyncButton) {
    noteSyncButton.onclick = async () => {
      noteSyncButton.classList.add("loading");
      noteSyncButton.disabled = true;
      try {
        const payload = await loadNoteSyncCard();
        renderNoteSyncPanel(payload);
        const desc = document.getElementById("noteSyncTriggerDesc");
        if (desc && payload) {
          desc.textContent = `${payload.noteCount ?? 0} 篇笔记，已绑定 ${payload.boundCount ?? 0} 篇`;
        }
      } finally {
        noteSyncButton.classList.remove("loading");
        noteSyncButton.disabled = false;
      }
    };

    const initialNotePayload = await loadNoteSyncCard();
    const desc = document.getElementById("noteSyncTriggerDesc");
    if (desc && initialNotePayload) {
      desc.textContent = `${initialNotePayload.noteCount ?? 0} 篇笔记，已绑定 ${initialNotePayload.boundCount ?? 0} 篇`;
    }
  }

  applyLive2dCollapsedState();
  const live2dToggleButton = document.getElementById("live2dToggleButton");
  if (live2dToggleButton && !live2dToggleButton.dataset.bound) {
    live2dToggleButton.addEventListener("click", toggleLive2dShell);
    live2dToggleButton.dataset.bound = "true";
  }
  await initLive2dWidget();

}

/* ═══════════════════════════════════════════
   Event listeners
═══════════════════════════════════════════ */
document.getElementById("logoutButton").addEventListener("click", () => { clearTokens(); redirectToLogin(); });
document.getElementById("openChatButton")?.addEventListener("click",       () => openChat(""));
document.getElementById("openChatButtonInline")?.addEventListener("click", () => openChat(""));
document.getElementById("openResearchAgentButtonInline")?.addEventListener("click", () => openResearchAgent());
document.getElementById("openImageAgentButtonInline")?.addEventListener("click", () => openImageAgent());
document.getElementById("navResearchAgentButton")?.addEventListener("click", () => openResearchAgent());
document.querySelector(".card-xhs-agent")?.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    openResearchAgent();
  }
});
document.querySelector(".card-image-agent")?.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    openImageAgent();
  }
});
document.getElementById("createAgentButton")?.addEventListener("click", createAgent);
document.getElementById("closeKbModal")?.addEventListener("click", closeKbModal);
document.getElementById("kbManageModal")?.addEventListener("click", e => {
  if (e.target === document.getElementById("kbManageModal")) closeKbModal();
});
document.getElementById("navChatButton").addEventListener("click",        () => openChat(""));
document.getElementById("newChatButton").addEventListener("click",        () => openChat(""));
document.getElementById("createKnowledgeBaseButton").addEventListener("click", createKnowledgeBase);
document.getElementById("uploadKnowledgeDocumentButton").addEventListener("click", () => {
  if (!selectedKnowledgeBaseId) {
    window.alert("请先创建并选中一个知识库");
    return;
  }
  document.getElementById("knowledgeDocumentFileInput").click();
});
document.getElementById("bindKnowledgeBaseButton").addEventListener("click", bindSelectedKnowledgeBaseToAgent);
document.getElementById("knowledgeDocumentFileInput").addEventListener("change", async event => {
  const file = event.target.files?.[0];
  if (!file) return;
  await uploadKnowledgeDocument(file);
  event.target.value = "";
});

loadWorkspace();
