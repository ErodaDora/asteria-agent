const ACCESS_TOKEN_KEY = "jagent.accessToken";
const REFRESH_TOKEN_KEY = "jagent.refreshToken";

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || "";
let refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || "";
let latestCrawlResult = null;
let latestGenerateResult = null;
let latestPublishStatus = "";
let mcpStatusPoller = null;
let pendingPublishRequest = null;

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

function parseLines(value) {
  return (value || "")
    .split(/[\n,，]/)
    .map(item => item.trim())
    .filter(Boolean);
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = value || "";
  }
}

function getPublishMode() {
  return document.getElementById("publishModeSelect")?.value || "mcp";
}

function getPublishOriginal() {
  return (document.getElementById("publishOriginalSelect")?.value || "true") === "true";
}

function getPublishVisibility() {
  return document.getElementById("publishVisibilitySelect")?.value || "公开可见";
}

function setMcpStatus(running, message) {
  const pill = document.getElementById("mcpStatusPill");
  const text = document.getElementById("mcpStatusText");
  if (!pill || !text) return;
  pill.classList.remove("is-running", "is-stopped", "is-checking");
  pill.classList.add(running ? "is-running" : "is-stopped");
  pill.textContent = running ? "运行中" : "未运行";
  text.textContent = message || (running ? "MCP 服务已就绪，可以直接发布图文内容。" : "请先启动并登录 MCP 服务，再使用 MCP 发布。");
}

function setStepState(stepId, state, pillText) {
  const el = document.getElementById(stepId);
  if (!el) return;
  el.classList.remove("step-pending", "step-current", "step-completed");
  el.classList.add(`step-${state}`);
  const pillIdMap = {
    stepCrawlCard: "stepCrawlPill",
    stepResultCard: "stepResultPill",
    stepGenerateCard: "stepGeneratePill",
    stepPublishCard: "stepPublishPill",
  };
  if (pillIdMap[stepId] && pillText) {
    setText(pillIdMap[stepId], pillText);
  }
}

function refreshWorkflowState() {
  const crawlStatus = latestCrawlResult?.status || "";
  const crawlCount = latestCrawlResult?.count || 0;
  const hasCrawlData = crawlStatus === "success" && crawlCount > 0;
  const hasGenerateData = Array.isArray(latestGenerateResult?.results) && latestGenerateResult.results.length > 0;
  const pageStatusText = document.getElementById("pageStatusText");
  const sideStatusText = document.getElementById("sideStatusText");

  if (crawlStatus === "login_required") {
    setStepState("stepCrawlCard", "current", "Login Required");
    setStepState("stepResultCard", "pending", "Pending");
    setStepState("stepGenerateCard", "pending", "Waiting");
    setStepState("stepPublishCard", "pending", "Standby");
    if (pageStatusText) pageStatusText.textContent = "等待登录";
    if (sideStatusText) sideStatusText.textContent = "登录中";
    return;
  }

  if (hasGenerateData) {
    setStepState("stepCrawlCard", "completed", "Captured");
    setStepState("stepResultCard", "completed", "Reviewed");
    setStepState("stepGenerateCard", "completed", "Generated");
    setStepState("stepPublishCard", "current", latestPublishStatus || "Ready to Publish");
    if (pageStatusText) pageStatusText.textContent = latestGenerateResult?.modelKey ? `生成完成 · ${latestGenerateResult.modelKey}` : "生成完成";
    if (sideStatusText) sideStatusText.textContent = "等待发布/归档";
    return;
  }

  if (hasCrawlData) {
    setStepState("stepCrawlCard", "completed", "Captured");
    setStepState("stepResultCard", "current", "Reviewing");
    setStepState("stepGenerateCard", "pending", "Waiting");
    setStepState("stepPublishCard", "pending", "Standby");
    if (pageStatusText) pageStatusText.textContent = `采集完成 · ${crawlCount} 条`;
    if (sideStatusText) sideStatusText.textContent = "进入分析与生成";
    return;
  }

  setStepState("stepCrawlCard", "current", "Ready");
  setStepState("stepResultCard", "pending", "Pending");
  setStepState("stepGenerateCard", "pending", "Waiting");
  setStepState("stepPublishCard", "pending", "Standby");
  if (pageStatusText) pageStatusText.textContent = "准备开始";
  if (sideStatusText) sideStatusText.textContent = "准备开始";
}

function renderMetric(label, value) {
  return `<span class="metric-pill"><span>${escapeHtml(label)}</span><span class="metric-value">${escapeHtml(value)}</span></span>`;
}

function buildCrawlPayload() {
  return {
    keywords: parseLines(document.getElementById("keywordsInput").value),
    topicWords: parseLines(document.getElementById("topicWordsInput").value),
    targetCount: Number(document.getElementById("targetCountInput").value || 3),
    minComments: Number(document.getElementById("minCommentsInput").value || 40),
    minLikes: Number(document.getElementById("minLikesInput").value || 100),
    minFavorites: Number(document.getElementById("minFavoritesInput").value || 0),
  };
}

function buildGeneratePayload() {
  return {
    datasetId: latestCrawlResult?.datasetId || "",
    modelKey: document.getElementById("modelKeySelect").value,
    audience: (document.getElementById("audienceInput").value || "").trim(),
    tone: document.getElementById("toneInput").value,
    topicCount: Number(document.getElementById("topicCountInput").value || 3),
    contentCountPerTopic: Number(document.getElementById("contentCountInput").value || 2),
    generateImages: document.getElementById("generateImagesInput").checked,
  };
}

function renderResults(result) {
  const summary = document.getElementById("resultSummary");
  const list = document.getElementById("resultList");
  const pageStatus = document.getElementById("pageStatusText");
  const crawlStatus = document.getElementById("crawlStatusText");

  if (!result) {
    latestCrawlResult = null;
    summary.textContent = "还没有采集结果。";
    list.innerHTML = "";
    refreshWorkflowState();
    return;
  }

  latestCrawlResult = result;

  summary.textContent = `datasetId: ${result.datasetId || "-"} · 已采集 ${result.count || 0}/${result.targetCount || 0} · 状态：${result.status || "-"}`;
  pageStatus.textContent = result.status || "已加载";
  crawlStatus.textContent = result.message || "";

  if (!Array.isArray(result.items) || result.items.length === 0) {
    list.innerHTML = `<div class="result-item"><div class="result-card-top"><h4>当前暂无笔记数据</h4><span class="result-card-tag">No Data</span></div><p class="result-card-copy">${escapeHtml(result.message || "等待 Playwright 采集接入。")}</p></div>`;
    refreshWorkflowState();
    return;
  }

  list.innerHTML = result.items.map(item => `
    <article class="result-item">
      <div class="result-card-top">
        <h4>${escapeHtml(item.title || "无标题")}</h4>
        <span class="result-card-tag">Captured</span>
      </div>
      <p class="result-card-copy">${escapeHtml((item.content || "").slice(0, 220) || "无正文")}</p>
      ${item.coverImageUrl ? `<div class="result-link-row"><span>首图链接</span><a href="${escapeHtml(item.coverImageUrl)}" target="_blank" rel="noreferrer">查看图片</a></div>` : ""}
      ${Array.isArray(item.hotComments) && item.hotComments.length ? `
        <div class="result-comments">
          <strong>高赞评论</strong>
          <ul>${item.hotComments.map(comment => `<li>${escapeHtml(comment)}</li>`).join("")}</ul>
        </div>
      ` : ""}
      <div class="result-metrics">
        ${renderMetric("作者", item.author || "-")}
        ${renderMetric("点赞", item.likes || 0)}
        ${renderMetric("收藏", item.favorites || 0)}
        ${renderMetric("评论", item.comments || 0)}
      </div>
    </article>
  `).join("");
  refreshWorkflowState();
}

function renderGenerateResults(result) {
  const summary = document.getElementById("generateSummary");
  const list = document.getElementById("generateResultList");
  const status = document.getElementById("generateStatusText");

  if (!result) {
    latestGenerateResult = null;
    summary.textContent = "还没有生成结果。";
    list.innerHTML = "";
    refreshWorkflowState();
    return;
  }

  latestGenerateResult = result;
  summary.textContent = `datasetId: ${result.datasetId || "-"} · 模型：${result.modelKey || "-"} · 选题数：${Array.isArray(result.results) ? result.results.length : 0}`;
  status.textContent = result.imageGenerationStatus || "生成完成";

  const analysisBlock = `
    <article class="result-item">
      <div class="result-card-top">
        <h4>分析摘要</h4>
        <span class="result-card-tag">Insight</span>
      </div>
      <p class="result-card-copy">${escapeHtml(result.analysisSummary || "暂无摘要")}</p>
      <div class="result-metrics">
        ${renderMetric("关键词", (result.topKeywords || []).join("、") || "-")}
        ${renderMetric("标签", (result.topTags || []).join("、") || "-")}
      </div>
    </article>
  `;

  const topicBlocks = Array.isArray(result.results) ? result.results.map((group, topicIndex) => `
    <article class="result-item">
      <div class="result-card-top">
        <h4>${escapeHtml(group.topic?.title || "未命名选题")}</h4>
        <span class="result-card-tag">Topic ${topicIndex + 1}</span>
      </div>
      <p class="result-card-copy">${escapeHtml(group.topic?.reason || "无选题说明")}</p>
      ${(group.contents || []).map((content, contentIndex) => `
        <div class="sub-result">
          <strong>${escapeHtml(content.title || "未命名文案")}</strong>
          <p>${escapeHtml(content.body || "无正文")}</p>
          <div class="result-metrics">
            ${renderMetric("类型", content.contentType || "-")}
            ${renderMetric("标签", (content.hashtags || []).join(" ") || "-")}
          </div>
          <div class="result-metrics">
            ${renderMetric("互动引导", content.cta || "-")}
            ${renderMetric("配图建议", content.imageSuggestion || "-")}
          </div>
          <div class="action-row">
            <button class="secondary-btn publish-generated-btn" data-topic-index="${topicIndex}" data-content-index="${contentIndex}" type="button">发布到小红书</button>
          </div>
        </div>
      `).join("")}
    </article>
  `).join("") : "";

  list.innerHTML = analysisBlock + topicBlocks;
  refreshWorkflowState();
}

function escapeHtml(text) {
  return String(text)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function buildPublishPayload(topicIndex, contentIndex) {
  return {
    datasetId: latestGenerateResult?.datasetId || "",
    topicIndex,
    contentIndex,
    mode: getPublishMode(),
    isOriginal: getPublishOriginal(),
    visibility: getPublishVisibility(),
  };
}

function openPublishPreview(preview, payload) {
  const overlay = document.getElementById("publishPreviewOverlay");
  const body = document.getElementById("publishPreviewBody");
  const status = document.getElementById("publishPreviewStatusText");
  if (!overlay || !body || !status) return;
  pendingPublishRequest = payload;
  body.innerHTML = `
    <section class="preview-card">
      <h4>标题</h4>
      <p class="preview-copy">${escapeHtml(preview.title || "未命名标题")}</p>
    </section>
    <section class="preview-card">
      <h4>正文与互动引导</h4>
      <p class="preview-copy">${escapeHtml([preview.body || "", preview.cta || "", (preview.hashtags || []).map(tag => `#${tag}`).join(" ")].filter(Boolean).join("\n\n"))}</p>
    </section>
    <section class="preview-card">
      <h4>发布设置</h4>
      <div class="result-metrics">
        ${renderMetric("模式", preview.publishMode || payload.mode || "mcp")}
        ${renderMetric("原创", preview.isOriginal ? "是" : "否")}
        ${renderMetric("可见性", preview.visibility || payload.visibility || "公开可见")}
      </div>
      ${preview.coverImageUrl ? `<img class="preview-cover" src="${escapeHtml(preview.coverImageUrl)}" alt="封面预览">` : ""}
    </section>
  `;
  status.textContent = "确认后会按当前发布模式执行。MCP 模式下会直接调用小红书 MCP 服务发布图文。";
  overlay.classList.remove("hidden");
}

function closePublishPreview() {
  const overlay = document.getElementById("publishPreviewOverlay");
  if (overlay) {
    overlay.classList.add("hidden");
  }
  pendingPublishRequest = null;
}

async function startCrawl() {
  const payload = buildCrawlPayload();

  const data = await authorizedCall("/api/xhs/crawl/search", payload);
  if (!data) return;
  setText("crawlStatusText", "正在执行检索与抓取...");
  setStepState("stepCrawlCard", "current", "Running");
  renderResults(data.data);
  if (data.data?.status === "login_required") {
    pollLoginStatus();
  }
}

function pollLoginStatus() {
  let retryCount = 0;
  const timer = setInterval(async () => {
    retryCount += 1;
    const data = await authorizedCall("/api/xhs/browser/login/status", null, { method: "GET" });
    if (!data) {
      clearInterval(timer);
      return;
    }
    const status = data.data;
    document.getElementById("crawlStatusText").textContent = status?.message || data.message || "已获取登录状态";
    if (!status?.loginWindowRunning || retryCount >= 30) {
      clearInterval(timer);
    }
  }, 2000);
}

async function refreshMcpStatus() {
  const data = await authorizedCall("/api/xhs/mcp/service/status", null, { method: "GET" });
  if (!data) return;
  const status = data.data;
  setMcpStatus(Boolean(status?.running), status?.message || "");
}

async function startMcpService(headless) {
  setText("mcpStatusText", headless ? "正在启动无界面 MCP 服务..." : "正在启动带浏览器的 MCP 服务...");
  const data = await authorizedCall(`/api/xhs/mcp/service/start?headless=${headless}`, null, { method: "POST" });
  if (!data) return;
  setMcpStatus(Boolean(data.data?.running), data.data?.message || data.message || "MCP 服务状态已更新");
}

async function stopMcpService() {
  const data = await authorizedCall("/api/xhs/mcp/service/stop", null, { method: "POST" });
  if (!data) return;
  setMcpStatus(Boolean(data.data?.running), data.data?.message || data.message || "MCP 服务已停止");
}

async function runMcpLogin() {
  const data = await authorizedCall("/api/xhs/mcp/service/login", null, { method: "POST" });
  if (!data) return;
  setText("mcpStatusText", data.data?.message || data.message || "登录工具已启动");
}

async function loadLatest() {
  const data = await authorizedCall("/api/xhs/crawl/latest", null, { method: "GET" });
  if (!data) return;
  renderResults(data.data);
}

async function startGenerate() {
  const payload = buildGeneratePayload();
  setText("generateStatusText", "正在分析并生成内容...");
  setStepState("stepGenerateCard", "current", "Generating");
  const data = await authorizedCall("/api/xhs/generate/run", payload);
  if (!data) return;
  renderGenerateResults(data.data);
}

async function runFullPipeline() {
  const crawlPayload = buildCrawlPayload();
  const firstKeyword = crawlPayload.keywords[0] || "";
  if (!firstKeyword) {
    setText("crawlStatusText", "请输入一个搜索关键词后再执行整链路");
    return;
  }
  crawlPayload.keywords = [firstKeyword];
  setText("crawlStatusText", `正在围绕关键词「${firstKeyword}」执行采集...`);
  setStepState("stepCrawlCard", "current", "Running");
  const crawlData = await authorizedCall("/api/xhs/crawl/search", crawlPayload);
  if (!crawlData) return;
  renderResults(crawlData.data);
  if (crawlData.data?.status === "login_required") {
    setText("generateStatusText", "采集前需要先登录，完成登录后再次点击一键跑完整流程");
    pollLoginStatus();
    return;
  }
  if (!Array.isArray(crawlData.data?.items) || crawlData.data.items.length === 0) {
    setText("generateStatusText", "采集已完成，但没有命中过滤条件的数据，已停止后续生成");
    return;
  }

  const generatePayload = buildGeneratePayload();
  setText("generateStatusText", "采集完成，正在自动进入分析与生成...");
  setStepState("stepGenerateCard", "current", "Generating");
  const generateData = await authorizedCall("/api/xhs/generate/run", generatePayload);
  if (!generateData) return;
  renderGenerateResults(generateData.data);
  setText("generateStatusText", "整链路执行完成，可以继续检查结果、同步 Notion 或直接发布");
}

async function loadLatestGenerate() {
  const data = await authorizedCall("/api/xhs/generate/latest", null, { method: "GET" });
  if (!data) return;
  renderGenerateResults(data.data);
}

async function syncToNotion() {
  const summary = document.getElementById("resultSummary").textContent || "";
  const match = summary.match(/datasetId:\s*([^\s]+)/);
  const datasetId = match ? match[1] : "";
  const data = await authorizedCall("/api/xhs/storage/notion/sync", { datasetId });
  if (!data) return;
  document.getElementById("notionStatusText").textContent = data.data?.message || data.message || "同步完成";
}

async function syncGeneratedToNotion() {
  const datasetId = latestGenerateResult?.datasetId || "";
  const data = await authorizedCall("/api/xhs/generated/notion/sync", { datasetId });
  if (!data) return;
  document.getElementById("generatedNotionStatusText").textContent = data.data?.message || data.message || "同步完成";
  latestPublishStatus = "Archived";
  refreshWorkflowState();
}

async function publishGenerated(topicIndex, contentIndex) {
  const payload = buildPublishPayload(topicIndex, contentIndex);
  document.getElementById("generateStatusText").textContent = "正在提交发布请求，这一步可能需要等待 1 到 5 分钟...";
  latestPublishStatus = "Publishing";
  refreshWorkflowState();
  const data = await authorizedCall("/api/xhs/generated/publish", payload);
  if (!data) return;
  document.getElementById("generateStatusText").textContent = data.data?.message || data.message || "发布完成";
  if (data.data?.loginRequired) {
    pollLoginStatus();
    return;
  }
  if (data.data?.manualActionRequired) {
    latestPublishStatus = "Manual Publish";
    refreshWorkflowState();
    return;
  }
  if (data.code && data.code !== 200) {
    latestPublishStatus = "Publish Failed";
    refreshWorkflowState();
    return;
  }
  latestPublishStatus = "Published";
  refreshWorkflowState();
}

async function previewPublishGenerated(topicIndex, contentIndex) {
  const payload = buildPublishPayload(topicIndex, contentIndex);
  document.getElementById("generateStatusText").textContent = "正在准备发布预览...";
  const data = await authorizedCall("/api/xhs/generated/publish/preview", payload);
  if (!data) return;
  openPublishPreview(data.data || {}, payload);
}

async function confirmPublishGenerated() {
  if (!pendingPublishRequest) return;
  const btn = document.getElementById("publishPreviewConfirmButton");
  const cancelBtn = document.getElementById("publishPreviewCancelButton");
  if (btn) btn.disabled = true;
  if (cancelBtn) cancelBtn.disabled = true;
  document.getElementById("publishPreviewStatusText").textContent = "正在提交发布请求...";
  try {
    await publishGenerated(pendingPublishRequest.topicIndex, pendingPublishRequest.contentIndex);
    closePublishPreview();
  } catch (e) {
    document.getElementById("publishPreviewStatusText").textContent = "发布失败，请重试";
    if (btn) btn.disabled = false;
    if (cancelBtn) cancelBtn.disabled = false;
  }
}

document.getElementById("backToWorkspaceButton")?.addEventListener("click", () => {
  window.location.href = "/workspace.html";
});
document.getElementById("startCrawlButton")?.addEventListener("click", startCrawl);
document.getElementById("runPipelineButton")?.addEventListener("click", runFullPipeline);
document.getElementById("loadLatestButton")?.addEventListener("click", loadLatest);
document.getElementById("syncToNotionButton")?.addEventListener("click", syncToNotion);
document.getElementById("startGenerateButton")?.addEventListener("click", startGenerate);
document.getElementById("loadLatestGenerateButton")?.addEventListener("click", loadLatestGenerate);
document.getElementById("syncGeneratedToNotionButton")?.addEventListener("click", syncGeneratedToNotion);
document.getElementById("mcpStartVisibleButton")?.addEventListener("click", () => startMcpService(false));
document.getElementById("mcpStartHeadlessButton")?.addEventListener("click", () => startMcpService(true));
document.getElementById("mcpStopButton")?.addEventListener("click", stopMcpService);
document.getElementById("mcpLoginButton")?.addEventListener("click", runMcpLogin);
document.getElementById("publishPreviewCloseButton")?.addEventListener("click", closePublishPreview);
document.getElementById("publishPreviewCancelButton")?.addEventListener("click", closePublishPreview);
document.getElementById("publishPreviewConfirmButton")?.addEventListener("click", confirmPublishGenerated);
document.getElementById("generateResultList")?.addEventListener("click", (event) => {
  const button = event.target.closest(".publish-generated-btn");
  if (!button) return;
  previewPublishGenerated(Number(button.dataset.topicIndex || 0), Number(button.dataset.contentIndex || 0));
});

loadLatest();
loadLatestGenerate();
refreshWorkflowState();
refreshMcpStatus();
if (mcpStatusPoller) {
  clearInterval(mcpStatusPoller);
}
mcpStatusPoller = setInterval(refreshMcpStatus, 10000);
