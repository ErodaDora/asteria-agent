const ACCESS_TOKEN_KEY = "jagent.accessToken";
const REFRESH_TOKEN_KEY = "jagent.refreshToken";
const AGENT_ID_KEY = "jagent.currentAgentId";
const PENDING_AGENT_PROMPT_KEY = "jagent.pendingAgentPrompt";
const WATERMARK_AGENT_ID = "10000000-0000-0000-0000-000000000002";

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || "";
let refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || "";

function redirectToLogin() {
  window.location.href = "/";
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
  if (!refreshToken) return null;
  const data = await callApi("/api/auth/refresh", { refreshToken });
  if (data.code === 200 && data.data) {
    accessToken = data.data.accessToken || "";
    refreshToken = data.data.refreshToken || "";
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    return data;
  }
  return null;
}

async function authorizedCall(url, payload, options = {}) {
  if (!accessToken) {
    redirectToLogin();
    return null;
  }
  let data = await callApi(url, payload, {
    ...options,
    headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` }
  });
  if (data?.message?.toLowerCase().match(/jwt|token|expired/)) {
    const ok = await refreshAccessToken();
    if (!ok) {
      redirectToLogin();
      return null;
    }
    data = await callApi(url, payload, {
      ...options,
      headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` }
    });
  }
  return data;
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value || "";
}

function setMcpStatus(running, message) {
  const pill = document.getElementById("mcpStatusPill");
  if (!pill) return;
  pill.classList.remove("is-running", "is-stopped", "is-checking");
  pill.classList.add(running ? "is-running" : "is-stopped");
  pill.textContent = running ? "运行中" : "未运行";
  setText("mcpStatusText", message || (running ? "Watermark MCP 已就绪。" : "请先启动 Watermark MCP 服务。"));
  setText("pageStatusText", running ? "MCP 已就绪" : "等待启动");
}

function renderSearchResults(matches) {
  const container = document.getElementById("searchResultList");
  if (!container) return;
  if (!Array.isArray(matches) || !matches.length) {
    container.innerHTML = `<div class="empty-state"><p>没有找到匹配图片。</p></div>`;
    return;
  }

  container.innerHTML = matches.map((item, index) => `
    <article class="result-item">
      <h4>候选图片 ${index + 1}</h4>
      <p>可直接用于水印嵌入。</p>
      <div class="result-path">${item}</div>
    </article>
  `).join("");
}

function renderEmbedResults(result) {
  const summary = document.getElementById("embedSummary");
  const list = document.getElementById("embedResultList");
  if (!summary || !list) return;

  if (!result) {
    summary.textContent = "还没有执行水印嵌入。";
    list.innerHTML = `<div class="empty-state"><p>等待执行。</p></div>`;
    return;
  }

  summary.textContent = result.output || "执行完成";
  const outputs = result.data?.outputs || [];
  if (!outputs.length) {
    list.innerHTML = `<div class="empty-state"><p>当前没有可展示的输出记录。</p></div>`;
    return;
  }

  list.innerHTML = outputs.map(item => `
    <article class="result-item">
      <h4>${item.success ? "处理成功" : "处理失败"}</h4>
      <p>${item.success ? `已完成水印嵌入，watermarkIndex=${item.watermarkIndex}` : (item.error || "未知错误")}</p>
      <div class="result-path">输入：${item.source || "-"}</div>
      ${item.output ? `<div class="result-path">输出：${item.output}</div>` : ""}
    </article>
  `).join("");
}

function buildSearchPayload() {
  return {
    toolName: "search_image_files",
    arguments: {
      directory: (document.getElementById("inputDirInput").value || "").trim(),
      keyword: (document.getElementById("keywordInput").value || "").trim(),
      limit: Number(document.getElementById("limitInput").value || 5),
    },
  };
}

function buildEmbedPayload() {
  return {
    inputDir: (document.getElementById("inputDirInput").value || "").trim(),
    outputDir: (document.getElementById("outputDirInput").value || "").trim(),
    limit: Number(document.getElementById("limitInput").value || 5),
  };
}

async function refreshMcpStatus() {
  const data = await authorizedCall("/api/watermark/mcp/status", null, { method: "GET" });
  if (!data || data.code !== 200 || !data.data) return;
  setMcpStatus(Boolean(data.data.running), data.data.message || "");
}

async function startMcp() {
  setText("workflowStatusText", "正在启动 Watermark MCP...");
  const data = await authorizedCall("/api/watermark/mcp/start", {});
  if (data?.code === 200 && data.data) {
    setMcpStatus(Boolean(data.data.running), data.data.message || "");
    setText("workflowStatusText", data.data.message || "Watermark MCP 已启动");
  }
}

async function stopMcp() {
  const data = await authorizedCall("/api/watermark/mcp/stop", {});
  if (data?.code === 200 && data.data) {
    setMcpStatus(Boolean(data.data.running), data.data.message || "");
    setText("workflowStatusText", data.data.message || "Watermark MCP 已停止");
  }
}

async function searchImages() {
  setText("workflowStatusText", "正在检索图片...");
  const data = await authorizedCall("/api/watermark/mcp/call", buildSearchPayload());
  if (!data || data.code !== 200 || !data.data) return;
  renderSearchResults(data.data.data?.matches || []);
  setText("workflowStatusText", data.data.output || "图片检索完成");
}

async function embedImages() {
  setText("workflowStatusText", "正在执行水印嵌入...");
  const data = await authorizedCall("/api/watermark/mcp/embed", buildEmbedPayload());
  if (!data || data.code !== 200 || !data.data) return;
  renderEmbedResults(data.data);
  setText("workflowStatusText", data.data.output || "水印嵌入完成");
}

function openAgentChat() {
  localStorage.setItem(AGENT_ID_KEY, WATERMARK_AGENT_ID);
  localStorage.removeItem(`jagent.agentSession.${WATERMARK_AGENT_ID}`);
  localStorage.setItem(
    PENDING_AGENT_PROMPT_KEY,
    "请帮我完成数字水印嵌入。先搜索输入目录中的图片文件，再选择合适的图片执行水印嵌入，并告诉我输出目录和生成结果。"
  );
  window.location.href = "/agent-chat.html";
}

document.getElementById("backToWorkspaceButton")?.addEventListener("click", () => {
  window.location.href = "/workspace.html";
});
document.getElementById("mcpStartButton")?.addEventListener("click", startMcp);
document.getElementById("mcpStopButton")?.addEventListener("click", stopMcp);
document.getElementById("searchImagesButton")?.addEventListener("click", searchImages);
document.getElementById("embedImagesButton")?.addEventListener("click", embedImages);
document.getElementById("openAgentChatButton")?.addEventListener("click", openAgentChat);

refreshMcpStatus();
