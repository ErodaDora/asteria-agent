const papersById = new Map();

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

async function apiCall(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  const payload = await response.json();
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || "请求失败");
  }
  return payload.data;
}

function getSearchParams() {
  const params = new URLSearchParams();
  params.set("query", (document.getElementById("queryInput")?.value || "").trim());
  params.set("scope", document.getElementById("scopeSelect")?.value || "all");
  params.set("venue", (document.getElementById("venueInput")?.value || "").trim());
  params.set("fromYear", document.getElementById("fromYearInput")?.value || "2022");
  const toYear = (document.getElementById("toYearInput")?.value || "").trim();
  if (toYear) {
    params.set("toYear", toYear);
  }
  params.set("limit", document.getElementById("limitSelect")?.value || "10");
  return params;
}

function paperKey(paper) {
  return paper.openalexId || paper.doi || paper.title;
}

function summarizeAbstract(text) {
  const value = String(text || "暂无摘要。");
  return value.length > 360 ? `${value.slice(0, 360)}...` : value;
}

function summarizeChunk(text) {
  const value = String(text || "");
  return value.length > 220 ? `${value.slice(0, 220)}...` : value;
}

function paperUrl(paper) {
  return paper.landingPageUrl || paper.doi || "#";
}

function renderResults(data) {
  const summaryEl = document.getElementById("resultsSummary");
  const listEl = document.getElementById("resultsList");
  const papers = Array.isArray(data?.papers) ? data.papers : [];
  papersById.clear();

  if (!papers.length) {
    const venueHint = data?.venueRaw ? `（已将 ${data.venueRaw} 识别为 ${data.venueCanonical || data.venueRaw}）` : "";
    summaryEl.textContent = `没有找到符合当前筛选条件的论文${venueHint}。`;
    listEl.innerHTML = `
      <div class="empty-state">
        <strong>没有匹配结果。</strong>
        <p>可以放宽年份、删去会议名，或换成更具体的英文关键词再试一次。</p>
      </div>`;
    return;
  }

  const venueHint = data.venueRaw ? `，来源匹配：${data.venueCanonical || data.venueRaw}` : "";
  const yearHint = data.toYear ? `，年份：${data.fromYear}-${data.toYear}` : `，年份：${data.fromYear} 以后`;
  summaryEl.textContent = `已返回 ${papers.length} 条${yearHint}${venueHint}`;
  listEl.innerHTML = papers.map(paper => {
    const key = paperKey(paper);
    papersById.set(key, paper);
    const hasPdf = Boolean(paper.pdfUrl);
    return `
      <article class="paper-row">
        <div>
          <h2 class="paper-title">
            <a href="${escapeHtml(paperUrl(paper))}" target="_blank" rel="noreferrer">${escapeHtml(paper.title || "Untitled")}</a>
          </h2>
          <div class="paper-meta">${escapeHtml(paper.publicationYear || "-")} · ${escapeHtml(paper.sourceType || "unknown")} · ${escapeHtml(paper.sourceName || "Unknown venue")}</div>
          <div class="paper-authors">${escapeHtml(paper.authors || "未知作者")}</div>
          <p class="paper-abstract">${escapeHtml(summarizeAbstract(paper.abstractText))}</p>
          <div class="paper-tags">
            ${paper.doi ? `<span class="paper-tag">DOI</span>` : ""}
            ${hasPdf ? `<span class="paper-tag">PDF 候选</span>` : ""}
            ${paper.openalexId ? `<span class="paper-tag">OpenAlex</span>` : ""}
          </div>
        </div>
        <div class="paper-actions">
          <button class="paper-action primary" type="button" data-save-paper="${escapeHtml(key)}">收藏入库</button>
          ${hasPdf ? `<a class="paper-action" href="${escapeHtml(paper.pdfUrl)}" target="_blank" rel="noreferrer">PDF</a>` : ""}
          <a class="paper-action" href="${escapeHtml(paperUrl(paper))}" target="_blank" rel="noreferrer">来源</a>
        </div>
      </article>`;
  }).join("");

  document.querySelectorAll("[data-save-paper]").forEach(button => {
    button.addEventListener("click", () => savePaper(button.dataset.savePaper, button));
  });
}

function renderCollections(collections) {
  const summaryEl = document.getElementById("collectionSummary");
  const listEl = document.getElementById("collectionList");
  const items = Array.isArray(collections) ? collections : [];
  summaryEl.textContent = `${items.length} 篇`;

  if (!items.length) {
    listEl.innerHTML = `
      <div class="empty-state">
        <strong>还没有收藏。</strong>
        <p>检索后点击“收藏入库”，这里会保留论文元数据和 PDF 候选链接。</p>
      </div>`;
    return;
  }

  listEl.innerHTML = items.map(item => {
    const paper = item.paper || {};
    return `
      <div class="collection-item">
        <p class="collection-title">${escapeHtml(paper.title || "Untitled")}</p>
        <div class="collection-meta">${escapeHtml(paper.publicationYear || "-")} · ${escapeHtml(paper.sourceName || "Unknown venue")}</div>
        <div class="collection-meta">${escapeHtml(item.status || "saved")} · ${escapeHtml(item.collectionName || "default")}</div>
        <div class="collection-actions">
          <button class="mini-action" type="button" data-index-paper="${escapeHtml(item.paperId || "")}">索引</button>
        </div>
      </div>`;
  }).join("");

  document.querySelectorAll("[data-index-paper]").forEach(button => {
    button.addEventListener("click", () => indexPaper(button.dataset.indexPaper, button));
  });
}

async function runSearch(prefillQuery = "") {
  const statusEl = document.getElementById("searchStatusText");
  const pageStatusEl = document.getElementById("pageStatusText");
  const button = document.getElementById("runSearchButton");
  const queryInput = document.getElementById("queryInput");

  if (prefillQuery) {
    queryInput.value = prefillQuery;
  }

  const params = getSearchParams();
  if (!params.get("query")) {
    statusEl.textContent = "先输入研究问题或关键词。";
    return;
  }

  button.disabled = true;
  statusEl.textContent = "正在检索公开论文数据...";
  pageStatusEl.textContent = "检索中";

  try {
    const data = await apiCall(`/api/research/papers/search?${params.toString()}`);
    renderResults(data);
    const venueText = data.venueRaw ? `（${data.venueRaw} -> ${data.venueCanonical}）` : "";
    statusEl.textContent = `检索完成${venueText}，可以继续收藏候选论文。`;
    pageStatusEl.textContent = "已完成";
  } catch (error) {
    statusEl.innerHTML = `<span class="error-line">${escapeHtml(error.message || "检索失败")}</span>`;
    pageStatusEl.textContent = "失败";
  } finally {
    button.disabled = false;
  }
}

async function savePaper(key, button) {
  const paper = papersById.get(key);
  if (!paper) return;
  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = "保存中";

  try {
    await apiCall("/api/research/papers/collections", {
      method: "POST",
      body: JSON.stringify({
        collectionName: "default",
        paper
      })
    });
    button.textContent = "已收藏";
    button.classList.add("toast-line");
    await loadCollections();
  } catch (error) {
    button.textContent = "保存失败";
    button.classList.add("error-line");
    setTimeout(() => {
      button.textContent = originalText;
      button.classList.remove("error-line");
      button.disabled = false;
    }, 1800);
    return;
  }

  setTimeout(() => {
    button.disabled = false;
    button.textContent = originalText;
    button.classList.remove("toast-line");
  }, 1400);
}

async function loadCollections() {
  const summaryEl = document.getElementById("collectionSummary");
  try {
    const collections = await apiCall("/api/research/papers/collections?collectionName=default");
    renderCollections(collections);
  } catch (error) {
    summaryEl.textContent = "未初始化";
    document.getElementById("collectionList").innerHTML = `
      <div class="empty-state">
        <strong>收藏表还不可用。</strong>
        <p>执行 sql/research_paper.sql 后即可保存论文收藏。</p>
      </div>`;
  }
}

async function indexPaper(paperId, button) {
  if (!paperId) return;
  const originalText = button.textContent;
  const statusEl = document.getElementById("indexStatusText");
  button.disabled = true;
  button.textContent = "索引中";
  statusEl.textContent = "正在生成摘要向量...";

  try {
    await apiCall(`/api/research/papers/${encodeURIComponent(paperId)}/index?collectionName=default`, {
      method: "POST"
    });
    statusEl.textContent = "已写入 pgvector 索引。";
    await loadCollections();
  } catch (error) {
    statusEl.innerHTML = `<span class="error-line">${escapeHtml(error.message || "索引失败")}</span>`;
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

async function indexCollection() {
  const button = document.getElementById("indexCollectionButton");
  const statusEl = document.getElementById("indexStatusText");
  button.disabled = true;
  statusEl.textContent = "正在批量索引收藏库...";

  try {
    const data = await apiCall("/api/research/papers/collections/default/index", {
      method: "POST"
    });
    statusEl.textContent = `已索引 ${data.indexedCount || 0} 篇论文。`;
    await loadCollections();
  } catch (error) {
    statusEl.innerHTML = `<span class="error-line">${escapeHtml(error.message || "索引失败")}</span>`;
  } finally {
    button.disabled = false;
  }
}

function renderSemanticResults(chunks) {
  const listEl = document.getElementById("semanticList");
  const items = Array.isArray(chunks) ? chunks : [];
  if (!items.length) {
    listEl.innerHTML = `
      <div class="semantic-item">
        <p class="semantic-title">暂无索引结果</p>
        <p class="semantic-content">先收藏论文并点击“索引收藏库”。</p>
      </div>`;
    return;
  }

  listEl.innerHTML = items.map(item => {
    const paper = item.paper || {};
    return `
      <div class="semantic-item">
        <p class="semantic-title">${escapeHtml(paper.title || "Untitled")}</p>
        <div class="collection-meta">${escapeHtml(paper.publicationYear || "-")} · ${escapeHtml(paper.sourceName || "Unknown venue")} · d=${Number(item.distance || 0).toFixed(3)}</div>
        <p class="semantic-content">${escapeHtml(summarizeChunk(item.content))}</p>
      </div>`;
  }).join("");
}

async function semanticSearch() {
  const queryInput = document.getElementById("semanticQueryInput");
  const button = document.getElementById("semanticSearchButton");
  const statusEl = document.getElementById("indexStatusText");
  const query = (queryInput?.value || "").trim();
  if (!query) {
    statusEl.textContent = "先输入要在收藏库里检索的问题。";
    return;
  }

  button.disabled = true;
  statusEl.textContent = "正在检索本地论文索引...";
  try {
    const params = new URLSearchParams({
      query,
      collectionName: "default",
      limit: "5"
    });
    const chunks = await apiCall(`/api/research/papers/semantic-search?${params.toString()}`);
    renderSemanticResults(chunks);
    statusEl.textContent = `命中 ${chunks.length} 条索引片段。`;
  } catch (error) {
    statusEl.innerHTML = `<span class="error-line">${escapeHtml(error.message || "检索失败")}</span>`;
  } finally {
    button.disabled = false;
  }
}

document.getElementById("backToWorkspaceButton")?.addEventListener("click", () => {
  window.location.href = "/workspace.html";
});

document.getElementById("runSearchButton")?.addEventListener("click", () => runSearch());
document.getElementById("fillMcpButton")?.addEventListener("click", () => runSearch("model context protocol mcp agent"));
document.getElementById("fillRagButton")?.addEventListener("click", () => runSearch("retrieval augmented generation rag evaluation"));
document.getElementById("indexCollectionButton")?.addEventListener("click", () => indexCollection());
document.getElementById("semanticSearchButton")?.addEventListener("click", () => semanticSearch());

loadCollections();
