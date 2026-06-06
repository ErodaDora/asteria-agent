package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.ResearchPaper;
import com.dora.jagent.model.entity.ResearchPaperCollection;
import com.dora.jagent.model.request.SaveResearchPaperRequest;
import com.dora.jagent.model.response.PaperSearchResponse;
import com.dora.jagent.model.response.ResearchPaperCollectionView;
import com.dora.jagent.model.response.ResearchPaperView;
import com.dora.jagent.repository.ResearchPaperCollectionRepository;
import com.dora.jagent.repository.ResearchPaperRepository;
import com.dora.jagent.service.PaperResearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaperResearchServiceImpl implements PaperResearchService {

    private static final String DEFAULT_COLLECTION = "default";
    private static final int DEFAULT_FROM_YEAR = 2022;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;

    private static final List<VenueAlias> VENUE_ALIASES = List.of(
            VenueAlias.builder()
                    .keys(List.of("acm", "acmmm", "acm mm", "acm multimedia", "mm"))
                    .canonical("ACM International Conference on Multimedia")
                    .searchBoost("ACM Multimedia")
                    .sourceIds(List.of("S4306417570", "S4363608757"))
                    .type("conference")
                    .build(),
            VenueAlias.builder()
                    .keys(List.of("tmm", "ieee tmm", "transactions on multimedia"))
                    .canonical("IEEE Transactions on Multimedia")
                    .searchBoost("IEEE Transactions on Multimedia")
                    .sourceIds(List.of("S204132540"))
                    .type("journal")
                    .build(),
            VenueAlias.builder()
                    .keys(List.of("cvpr"))
                    .canonical("IEEE/CVF Conference on Computer Vision and Pattern Recognition")
                    .searchBoost("CVPR")
                    .sourceIds(List.of())
                    .type("conference")
                    .build(),
            VenueAlias.builder()
                    .keys(List.of("acl"))
                    .canonical("Annual Meeting of the Association for Computational Linguistics")
                    .searchBoost("ACL")
                    .sourceIds(List.of())
                    .type("conference")
                    .build()
    );

    private final ResearchPaperRepository researchPaperRepository;
    private final ResearchPaperCollectionRepository researchPaperCollectionRepository;
    private final ObjectMapper objectMapper;

    @Value("${research.openalex.base-url:https://api.openalex.org}")
    private String openAlexBaseUrl;

    @Override
    public PaperSearchResponse searchPapers(
            String query,
            String scope,
            String venue,
            Integer fromYear,
            Integer toYear,
            Integer limit
    ) {
        if (!StringUtils.hasText(query)) {
            throw new BizException("query cannot be blank");
        }

        String safeScope = normalizeScope(scope);
        int safeFromYear = fromYear == null ? DEFAULT_FROM_YEAR : fromYear;
        Integer safeToYear = normalizeToYear(toYear, safeFromYear);
        int safeLimit = normalizeLimit(limit);
        VenueMatch venueMatch = normalizeVenue(venue);
        List<String> resolvedSourceIds = resolveVenueSources(venueMatch, safeScope);
        Set<String> sourceIds = new LinkedHashSet<>();
        sourceIds.addAll(venueMatch.getSourceIds());
        sourceIds.addAll(resolvedSourceIds);

        JsonNode works = fetchWorks(query.trim(), safeScope, venueMatch, sourceIds, safeFromYear, safeToYear, safeLimit, false);
        List<ResearchPaperView> papers = parseWorks(works, venueMatch, safeLimit);
        if (StringUtils.hasText(venueMatch.getRaw()) && !sourceIds.isEmpty() && papers.size() < safeLimit) {
            JsonNode fallbackWorks = fetchWorks(
                    query.trim(),
                    safeScope,
                    venueMatch,
                    Set.of(),
                    safeFromYear,
                    safeToYear,
                    safeLimit,
                    true
            );
            papers = mergePapers(papers, parseWorks(fallbackWorks, venueMatch, safeLimit), safeLimit);
        }
        int openAlexTotal = works.path("meta").path("count").asInt(papers.size());

        return PaperSearchResponse.builder()
                .query(query.trim())
                .scope(safeScope)
                .venueRaw(venueMatch.getRaw())
                .venueCanonical(venueMatch.getCanonical())
                .fromYear(safeFromYear)
                .toYear(safeToYear)
                .limit(safeLimit)
                .total(openAlexTotal)
                .source("OpenAlex")
                .papers(papers)
                .build();
    }

    @Override
    public ResearchPaperCollectionView savePaper(SaveResearchPaperRequest request) {
        if (request == null || request.getPaper() == null || !StringUtils.hasText(request.getPaper().getTitle())) {
            throw new BizException("paper title cannot be blank");
        }

        ResearchPaperView input = request.getPaper();
        String collectionName = StringUtils.hasText(request.getCollectionName())
                ? request.getCollectionName().trim()
                : DEFAULT_COLLECTION;
        LocalDateTime now = LocalDateTime.now();

        ResearchPaper existing = researchPaperRepository.findByOpenalexId(input.getOpenalexId())
                .or(() -> researchPaperRepository.findByDoi(input.getDoi()))
                .orElse(null);

        ResearchPaper paper = toEntity(input, existing == null ? UUID.randomUUID().toString() : existing.getId(), now);
        if (existing != null && existing.getCreatedAt() != null) {
            paper.setCreatedAt(existing.getCreatedAt());
        }
        ResearchPaper savedPaper = researchPaperRepository.save(paper);

        ResearchPaperCollection collection = ResearchPaperCollection.builder()
                .id(UUID.randomUUID().toString())
                .paperId(savedPaper.getId())
                .collectionName(collectionName)
                .note(trimToNull(request.getNote()))
                .status("saved")
                .createdAt(now)
                .updatedAt(now)
                .build();
        ResearchPaperCollection savedCollection = researchPaperCollectionRepository.save(collection);
        return toCollectionView(savedCollection, savedPaper);
    }

    @Override
    public List<ResearchPaperCollectionView> getCollections(String collectionName) {
        String safeCollectionName = StringUtils.hasText(collectionName) ? collectionName.trim() : DEFAULT_COLLECTION;
        return researchPaperCollectionRepository.findByCollectionNameOrderByCreatedAtDesc(safeCollectionName).stream()
                .map(collection -> {
                    ResearchPaper paper = researchPaperRepository.findById(collection.getPaperId()).orElse(null);
                    return toCollectionView(collection, paper);
                })
                .toList();
    }

    private JsonNode fetchWorks(
            String query,
            String scope,
            VenueMatch venue,
            Set<String> sourceIds,
            int fromYear,
            Integer toYear,
            int limit,
            boolean broadVenueSearch
    ) {
        int perPage = broadVenueSearch ? Math.max(limit * 10, 100) : (StringUtils.hasText(venue.getRaw()) ? Math.max(limit * 3, 25) : limit);
        String searchText = query;
        if ((broadVenueSearch || sourceIds.isEmpty()) && StringUtils.hasText(venue.getRaw()) && StringUtils.hasText(venue.getSearchBoost())) {
            searchText = query + " " + venue.getSearchBoost();
        }
        final String openAlexSearch = searchText;

        List<String> filters = new ArrayList<>();
        filters.add("from_publication_date:" + fromYear + "-01-01");
        if (toYear != null) {
            filters.add("to_publication_date:" + toYear + "-12-31");
        }
        if ("journal".equals(scope)) {
            filters.add("primary_location.source.type:journal");
        } else if ("conference".equals(scope) || "conference".equals(venue.getType())) {
            filters.add("primary_location.source.type:conference");
        } else if ("journal".equals(venue.getType())) {
            filters.add("primary_location.source.type:journal");
        }
        if (!sourceIds.isEmpty()) {
            filters.add("primary_location.source.id:" + String.join("|", sourceIds));
        }

        RestClient restClient = RestClient.builder().baseUrl(openAlexBaseUrl).build();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/works")
                        .queryParam("search", openAlexSearch)
                        .queryParam("filter", String.join(",", filters))
                        .queryParam("per-page", perPage)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private List<String> resolveVenueSources(VenueMatch venue, String scope) {
        if (!StringUtils.hasText(venue.getRaw())) {
            return List.of();
        }

        try {
            RestClient restClient = RestClient.builder().baseUrl(openAlexBaseUrl).build();
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sources")
                            .queryParam("search", venue.getCanonical())
                            .queryParam("per-page", 10)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            List<String> sourceIds = new ArrayList<>();
            JsonNode results = response == null ? null : response.path("results");
            if (results != null && results.isArray()) {
                for (JsonNode source : results) {
                    String type = source.path("type").asText("");
                    if ("journal".equals(scope) && !"journal".equals(type)) continue;
                    if ("conference".equals(scope) && !"conference".equals(type)) continue;
                    if (StringUtils.hasText(venue.getType()) && !venue.getType().equals(type)) continue;
                    if (matchesSourceAlias(source, venue)) {
                        sourceIds.add(openAlexSourceId(source.path("id").asText("")));
                    }
                    if (sourceIds.size() >= 8) break;
                }
            }
            return sourceIds;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ResearchPaperView> parseWorks(JsonNode works, VenueMatch venue, int limit) {
        JsonNode results = works == null ? null : works.path("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<ResearchPaperView> papers = new ArrayList<>();
        for (JsonNode work : results) {
            ResearchPaperView paper = toPaperView(work);
            if (StringUtils.hasText(venue.getRaw()) && !matchesPaperVenue(paper, venue)) {
                continue;
            }
            papers.add(paper);
            if (papers.size() >= limit) {
                break;
            }
        }
        return papers;
    }

    private List<ResearchPaperView> mergePapers(
            List<ResearchPaperView> primary,
            List<ResearchPaperView> fallback,
            int limit
    ) {
        List<ResearchPaperView> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ResearchPaperView paper : primary) {
            if (seen.add(paperUniqueKey(paper))) {
                merged.add(paper);
            }
        }
        for (ResearchPaperView paper : fallback) {
            if (seen.add(paperUniqueKey(paper))) {
                merged.add(paper);
            }
            if (merged.size() >= limit) break;
        }
        return merged;
    }

    private ResearchPaperView toPaperView(JsonNode work) {
        JsonNode source = work.path("primary_location").path("source");
        String sourceName = source.path("display_name").asText("Unknown venue");
        String sourceType = source.path("type").asText("unknown");
        String openAlexId = openAlexSourceId(work.path("id").asText(""));
        String landingPageUrl = textOrNull(work.path("primary_location").path("landing_page_url"));
        String pdfUrl = firstText(
                work.path("primary_location").path("pdf_url"),
                work.path("best_oa_location").path("pdf_url"),
                work.path("open_access").path("oa_url")
        );
        String metadata = buildMetadata(work);

        return ResearchPaperView.builder()
                .openalexId(openAlexId)
                .doi(textOrNull(work.path("doi")))
                .title(work.path("title").asText("Untitled"))
                .abstractText(extractAbstract(work))
                .publicationYear(work.path("publication_year").isInt() ? work.path("publication_year").asInt() : null)
                .sourceName(sourceName)
                .sourceType(sourceType)
                .authors(normalizeAuthors(work.path("authorships")))
                .landingPageUrl(landingPageUrl)
                .pdfUrl(pdfUrl)
                .metadata(metadata)
                .build();
    }

    private String buildMetadata(JsonNode work) {
        try {
            JsonNode source = work.path("primary_location").path("source");
            Map<String, Object> metadata = Map.of(
                    "citedByCount", work.path("cited_by_count").asInt(0),
                    "openAlexUrl", work.path("id").asText(""),
                    "sourceOpenAlexId", openAlexSourceId(source.path("id").asText(""))
            );
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractAbstract(JsonNode work) {
        String abstractText = textOrNull(work.path("abstract"));
        if (StringUtils.hasText(abstractText)) {
            return abstractText;
        }

        JsonNode inverted = work.path("abstract_inverted_index");
        if (!inverted.isObject()) {
            return "暂无摘要。";
        }

        List<PositionedWord> words = new ArrayList<>();
        inverted.fields().forEachRemaining(entry -> {
            JsonNode positions = entry.getValue();
            if (positions.isArray()) {
                for (JsonNode position : positions) {
                    if (position.isInt()) {
                        words.add(new PositionedWord(position.asInt(), entry.getKey()));
                    }
                }
            }
        });
        words.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        StringBuilder builder = new StringBuilder();
        for (PositionedWord word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(word.getWord());
        }
        if (builder.isEmpty()) {
            return "暂无摘要。";
        }
        return builder.length() > 720 ? builder.substring(0, 720) : builder.toString();
    }

    private String normalizeAuthors(JsonNode authorships) {
        if (authorships == null || !authorships.isArray() || authorships.isEmpty()) {
            return "未知作者";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode authorship : authorships) {
            String name = authorship.path("author").path("display_name").asText("");
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
            if (names.size() >= 4) break;
        }
        return names.isEmpty() ? "未知作者" : String.join(", ", names);
    }

    private boolean matchesPaperVenue(ResearchPaperView paper, VenueMatch venue) {
        if (!StringUtils.hasText(venue.getRaw())) return true;
        String haystack = String.valueOf(paper.getSourceName()).toLowerCase(Locale.ROOT);
        String raw = venue.getRaw().toLowerCase(Locale.ROOT).replaceAll("[-_]+", " ").replaceAll("\\s+", " ");
        if ("acm".equals(raw) || "acmmm".equals(raw) || "acm mm".equals(raw) || "mm".equals(raw)) {
            return haystack.contains("acm") && haystack.contains("multimedia");
        }
        List<String> needles = List.of(venue.getRaw(), venue.getCanonical(), venue.getSearchBoost()).stream()
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        return needles.stream().anyMatch(haystack::contains);
    }

    private boolean matchesSourceAlias(JsonNode source, VenueMatch venue) {
        String sourceId = openAlexSourceId(source.path("id").asText(""));
        if (venue.getSourceIds().contains(sourceId)) return true;

        String name = source.path("display_name").asText("").toLowerCase(Locale.ROOT);
        String raw = venue.getRaw().toLowerCase(Locale.ROOT);
        String canonical = venue.getCanonical().toLowerCase(Locale.ROOT);
        String boost = venue.getSearchBoost().toLowerCase(Locale.ROOT);

        if (StringUtils.hasText(canonical) && name.contains(canonical)) return true;
        if (StringUtils.hasText(boost) && name.contains(boost)) return true;
        if (raw.length() > 3 && name.contains(raw)) return true;

        if ("acm".equals(raw) || "acmmm".equals(raw) || "acm mm".equals(raw) || "mm".equals(raw)) {
            return name.contains("acm") && name.contains("multimedia");
        }
        return false;
    }

    private VenueMatch normalizeVenue(String value) {
        String raw = StringUtils.hasText(value) ? value.trim() : "";
        String lower = raw.toLowerCase(Locale.ROOT).replaceAll("[-_]+", " ").replaceAll("\\s+", " ");
        if (!StringUtils.hasText(lower)) {
            return VenueMatch.empty();
        }

        return VENUE_ALIASES.stream()
                .filter(alias -> alias.getKeys().contains(lower))
                .findFirst()
                .map(alias -> VenueMatch.builder()
                        .raw(raw)
                        .canonical(alias.getCanonical())
                        .searchBoost(alias.getSearchBoost())
                        .sourceIds(alias.getSourceIds())
                        .type(alias.getType())
                        .build())
                .orElseGet(() -> VenueMatch.builder()
                        .raw(raw)
                        .canonical(raw)
                        .searchBoost(raw)
                        .sourceIds(List.of())
                        .type("")
                        .build());
    }

    private String normalizeScope(String scope) {
        if ("journal".equals(scope) || "conference".equals(scope)) {
            return scope;
        }
        return "all";
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Integer normalizeToYear(Integer toYear, int fromYear) {
        if (toYear == null || toYear < 1) {
            return null;
        }
        if (toYear < fromYear) {
            throw new BizException("toYear cannot be earlier than fromYear");
        }
        return toYear;
    }

    private String paperUniqueKey(ResearchPaperView paper) {
        if (StringUtils.hasText(paper.getOpenalexId())) {
            return paper.getOpenalexId();
        }
        if (StringUtils.hasText(paper.getDoi())) {
            return paper.getDoi();
        }
        return String.valueOf(paper.getTitle());
    }

    private ResearchPaper toEntity(ResearchPaperView view, String id, LocalDateTime now) {
        return ResearchPaper.builder()
                .id(id)
                .openalexId(trimToNull(view.getOpenalexId()))
                .doi(trimToNull(view.getDoi()))
                .title(view.getTitle().trim())
                .abstractText(trimToNull(view.getAbstractText()))
                .publicationYear(view.getPublicationYear())
                .sourceName(trimToNull(view.getSourceName()))
                .sourceType(trimToNull(view.getSourceType()))
                .authors(trimToNull(view.getAuthors()))
                .landingPageUrl(trimToNull(view.getLandingPageUrl()))
                .pdfUrl(trimToNull(view.getPdfUrl()))
                .metadata(StringUtils.hasText(view.getMetadata()) ? view.getMetadata() : "{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private ResearchPaperView toView(ResearchPaper paper) {
        if (paper == null) return null;
        return ResearchPaperView.builder()
                .id(paper.getId())
                .openalexId(paper.getOpenalexId())
                .doi(paper.getDoi())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .publicationYear(paper.getPublicationYear())
                .sourceName(paper.getSourceName())
                .sourceType(paper.getSourceType())
                .authors(paper.getAuthors())
                .landingPageUrl(paper.getLandingPageUrl())
                .pdfUrl(paper.getPdfUrl())
                .metadata(paper.getMetadata())
                .createdAt(paper.getCreatedAt())
                .updatedAt(paper.getUpdatedAt())
                .build();
    }

    private ResearchPaperCollectionView toCollectionView(ResearchPaperCollection collection, ResearchPaper paper) {
        return ResearchPaperCollectionView.builder()
                .id(collection.getId())
                .paperId(collection.getPaperId())
                .collectionName(collection.getCollectionName())
                .note(collection.getNote())
                .status(collection.getStatus())
                .paper(toView(paper))
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }

    private String openAlexSourceId(String value) {
        return String.valueOf(value == null ? "" : value).replace("https://openalex.org/", "");
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String text = node.asText("");
        return StringUtils.hasText(text) ? text : null;
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = textOrNull(node);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class VenueAlias {
        private List<String> keys;
        private String canonical;
        private String searchBoost;
        private List<String> sourceIds;
        private String type;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class VenueMatch {
        private String raw;
        private String canonical;
        private String searchBoost;
        private List<String> sourceIds;
        private String type;

        static VenueMatch empty() {
            return VenueMatch.builder()
                    .raw("")
                    .canonical("")
                    .searchBoost("")
                    .sourceIds(List.of())
                    .type("")
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    private static class PositionedWord {
        private int position;
        private String word;
    }
}
