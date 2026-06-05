function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

const VENUE_ALIASES = [
  {
    keys: ["acm", "acmmm", "acm mm", "acm multimedia", "mm"],
    canonical: "ACM International Conference on Multimedia",
    searchBoost: "ACM Multimedia",
    sourceIds: [
      "S4306417570",
      "S4363608757"
    ],
    type: "conference"
  },
  {
    keys: ["tmm", "ieee tmm", "transactions on multimedia"],
    canonical: "IEEE Transactions on Multimedia",
    searchBoost: "IEEE Transactions on Multimedia",
    sourceIds: ["S204132540"],
    type: "journal"
  },
  {
    keys: ["cvpr"],
    canonical: "IEEE/CVF Conference on Computer Vision and Pattern Recognition",
    searchBoost: "CVPR",
    sourceIds: [],
    type: "conference"
  },
  {
    keys: ["acl"],
    canonical: "Annual Meeting of the Association for Computational Linguistics",
    searchBoost: "ACL",
    sourceIds: [],
    type: "conference"
  }
];

function normalizeVenueInput(value) {
  const raw = (value || "").trim();
  const lower = raw.toLowerCase().replace(/[-_]+/g, " ").replace(/\s+/g, " ");
  if (!lower) {
    return {
      raw: "",
      canonical: "",
      searchBoost: "",
      sourceIds: [],
      type: ""
    };
  }

  const alias = VENUE_ALIASES.find(item => item.keys.includes(lower));
  if (alias) {
    return {
      raw,
      canonical: alias.canonical,
      searchBoost: alias.searchBoost,
      sourceIds: alias.sourceIds,
      type: alias.type
    };
  }

  return {
    raw,
    canonical: raw,
    searchBoost: raw,
    sourceIds: [],
    type: ""
  };
}

function openAlexSourceId(value) {
  return String(value || "").replace(/^https:\/\/openalex\.org\//, "");
}

function matchesVenueName(work, venue) {
  if (!venue.raw && !venue.canonical) return true;
  const source = work?.primary_location?.source || {};
  const sourceId = openAlexSourceId(source.id);
  if (venue.sourceIds?.includes(sourceId)) return true;

  const haystack = [
    source.display_name,
    ...(Array.isArray(source.alternate_titles) ? source.alternate_titles : [])
  ].filter(Boolean).join(" ").toLowerCase();

  const needles = [venue.raw, venue.canonical, venue.searchBoost]
    .filter(Boolean)
    .map(item => item.toLowerCase());

  return needles.some(needle => haystack.includes(needle));
}

async function resolveVenueSources(venue, scope) {
  if (!venue.raw) return [];

  const params = new URLSearchParams();
  params.set("search", venue.canonical || venue.raw);
  params.set("per-page", "10");
  try {
    const response = await fetch(`https://api.openalex.org/sources?${params.toString()}`);
    if (!response.ok) return [];

    const data = await response.json();
    const results = Array.isArray(data?.results) ? data.results : [];
    return results
      .filter(source => {
        if (scope === "journal") return source?.type === "journal";
        if (scope === "conference") return source?.type === "conference";
        if (venue.type) return source?.type === venue.type;
        return true;
      })
      .filter(source => matchesSourceAlias(source, venue))
      .map(source => openAlexSourceId(source.id))
      .filter(Boolean)
      .slice(0, 8);
  } catch (error) {
    return [];
  }
}

function matchesSourceAlias(source, venue) {
  if (!venue.raw && !venue.canonical) return true;
  const sourceId = openAlexSourceId(source?.id);
  if (venue.sourceIds?.includes(sourceId)) return true;

  const name = String(source?.display_name || "").toLowerCase();
  const raw = String(venue.raw || "").toLowerCase();
  const canonical = String(venue.canonical || "").toLowerCase();
  const boost = String(venue.searchBoost || "").toLowerCase();

  if (canonical && name.includes(canonical)) return true;
  if (boost && name.includes(boost)) return true;
  if (raw.length > 3 && name.includes(raw)) return true;

  if (raw === "acm" || raw === "acmmm" || raw === "acm mm" || raw === "mm") {
    return name.includes("acm") && name.includes("multimedia");
  }
  return false;
}

function buildQueryUrl(extra = {}) {
  const query = (document.getElementById("queryInput")?.value || "").trim();
  const scope = document.getElementById("scopeSelect")?.value || "all";
  const venue = normalizeVenueInput(document.getElementById("venueInput")?.value || "");
  const fromYear = Number(document.getElementById("fromYearInput")?.value || 2022);
  const limit = Number(document.getElementById("limitSelect")?.value || 10);

  const params = new URLSearchParams();
  const searchParts = [query];
  if (venue.raw && !extra.sourceIds?.length && venue.searchBoost) {
    searchParts.push(venue.searchBoost);
  }
  params.set("search", searchParts.filter(Boolean).join(" "));
  params.set("per-page", String(extra.perPage || limit));

  const filters = [`from_publication_date:${fromYear}-01-01`];
  if (scope === "journal") {
    filters.push("primary_location.source.type:journal");
  } else if (scope === "conference" || venue.type === "conference") {
    filters.push("primary_location.source.type:conference");
  } else if (venue.type === "journal") {
    filters.push("primary_location.source.type:journal");
  }
  if (extra.sourceIds?.length) {
    filters.push(`primary_location.source.id:${extra.sourceIds.join("|")}`);
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
  if (meta.venue?.raw) {
    filtered = filtered.filter(work => matchesVenueName(work, meta.venue));
    if (filtered.length > meta.limit) {
      filtered = filtered.slice(0, meta.limit);
    }
  }

  if (!filtered.length) {
    const venueHint = meta.venue?.raw ? `（已将 ${meta.venue.raw} 识别为 ${meta.venue.canonical || meta.venue.raw}）` : "";
    summaryEl.textContent = `没有找到符合当前筛选条件的论文${venueHint}。`;
    listEl.innerHTML = "";
    return;
  }

  const venueHint = meta.venue?.raw ? `，来源匹配：${meta.venue.canonical || meta.venue.raw}` : "";
  summaryEl.textContent = `已找到 ${filtered.length} 条结果${venueHint}，可按标题、来源和摘要做初筛。`;
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

  const firstMeta = buildQueryUrl();
  if (!firstMeta.query) {
    statusEl.textContent = "先输入研究问题或关键词。";
    return;
  }

  statusEl.textContent = "正在检索公开论文数据…";
  pageStatusEl.textContent = "检索中";

  try {
    const sourceIds = await resolveVenueSources(firstMeta.venue, firstMeta.scope);
    const meta = buildQueryUrl({
      sourceIds: [...new Set([...(firstMeta.venue.sourceIds || []), ...sourceIds])],
      perPage: firstMeta.venue.raw ? Math.max(firstMeta.limit * 3, 25) : firstMeta.limit
    });
    if (meta.venue.raw && meta.venue.canonical) {
      statusEl.textContent = `正在检索公开论文数据（${meta.venue.raw} → ${meta.venue.canonical}）…`;
    }
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
