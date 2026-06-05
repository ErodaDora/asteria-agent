function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function buildQueryUrl() {
  const query = (document.getElementById("queryInput")?.value || "").trim();
  const scope = document.getElementById("scopeSelect")?.value || "all";
  const venue = (document.getElementById("venueInput")?.value || "").trim().toLowerCase();
  const fromYear = Number(document.getElementById("fromYearInput")?.value || 2022);
  const limit = Number(document.getElementById("limitSelect")?.value || 10);

  const params = new URLSearchParams();
  params.set("search", query);
  params.set("per-page", String(limit));

  const filters = [`from_publication_date:${fromYear}-01-01`];
  if (scope === "journal") {
    filters.push("primary_location.source.type:journal");
  } else if (scope === "conference") {
    filters.push("primary_location.source.type:conference");
  }
  params.set("filter", filters.join(","));

  return {
    url: `https://api.openalex.org/works?${params.toString()}`,
    query,
    venue,
    scope,
    fromYear,
    limit
  };
}

function normalizeAuthors(authorships) {
  if (!Array.isArray(authorships) || authorships.length === 0) return "未知作者";
  return authorships
    .slice(0, 4)
    .map(item => item?.author?.display_name)
    .filter(Boolean)
    .join(", ");
}

function extractAbstract(work) {
  const text = work?.abstract || work?.summary || "";
  if (text) return text;
  const inverted = work?.abstract_inverted_index;
  if (!inverted || typeof inverted !== "object") return "暂无摘要。";

  const pairs = [];
  Object.entries(inverted).forEach(([word, positions]) => {
    positions.forEach(pos => pairs.push([pos, word]));
  });
  return pairs
    .sort((a, b) => a[0] - b[0])
    .map(([, word]) => word)
    .join(" ")
    .slice(0, 420) || "暂无摘要。";
}

function renderResults(works, meta) {
  const summaryEl = document.getElementById("resultsSummary");
  const listEl = document.getElementById("resultsList");

  let filtered = works || [];
  if (meta.venue) {
    filtered = filtered.filter(work => {
      const venueName = work?.primary_location?.source?.display_name || "";
      return venueName.toLowerCase().includes(meta.venue);
    });
  }

  if (!filtered.length) {
    summaryEl.textContent = "没有找到符合当前筛选条件的论文。";
    listEl.innerHTML = "";
    return;
  }

  summaryEl.textContent = `已找到 ${filtered.length} 条结果，可按标题、来源和摘要做初筛。`;
  listEl.innerHTML = filtered.map(work => {
    const title = work?.title || "Untitled";
    const year = work?.publication_year || "-";
    const venue = work?.primary_location?.source?.display_name || "Unknown venue";
    const type = work?.primary_location?.source?.type || "unknown";
    const authors = normalizeAuthors(work?.authorships);
    const url = work?.primary_location?.landing_page_url || work?.doi || "#";
    const abstract = extractAbstract(work);
    return `
      <article class="result-card">
        <h4 class="result-title"><a href="${escapeHtml(url)}" target="_blank" rel="noreferrer">${escapeHtml(title)}</a></h4>
        <div class="result-meta">${escapeHtml(String(year))} · ${escapeHtml(type)} · ${escapeHtml(venue)}</div>
        <div class="result-meta">${escapeHtml(authors)}</div>
        <p class="result-abstract">${escapeHtml(abstract)}</p>
      </article>`;
  }).join("");
}

async function runSearch(prefillQuery = "") {
  const statusEl = document.getElementById("searchStatusText");
  const pageStatusEl = document.getElementById("pageStatusText");
  const queryInput = document.getElementById("queryInput");

  if (prefillQuery) {
    queryInput.value = prefillQuery;
  }

  const meta = buildQueryUrl();
  if (!meta.query) {
    statusEl.textContent = "先输入研究问题或关键词。";
    return;
  }

  statusEl.textContent = "正在检索公开论文数据…";
  pageStatusEl.textContent = "检索中";

  try {
    const response = await fetch(meta.url);
    const data = await response.json();
    const works = Array.isArray(data?.results) ? data.results : [];
    renderResults(works, meta);
    statusEl.textContent = "检索完成，可以继续筛来源与摘要。";
    pageStatusEl.textContent = "已完成";
  } catch (error) {
    statusEl.textContent = "检索失败，可能是网络或公开数据源暂时不可用。";
    pageStatusEl.textContent = "失败";
  }
}

document.getElementById("backToWorkspaceButton")?.addEventListener("click", () => {
  window.location.href = "/workspace.html";
});

document.getElementById("runSearchButton")?.addEventListener("click", () => runSearch());
document.getElementById("fillMcpButton")?.addEventListener("click", () => runSearch("model context protocol mcp agent"));
document.getElementById("fillRagButton")?.addEventListener("click", () => runSearch("retrieval augmented generation rag evaluation"));
