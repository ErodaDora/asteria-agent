package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.SaveResearchPaperRequest;
import com.dora.jagent.model.response.PaperIndexResponse;
import com.dora.jagent.model.response.PaperSearchResponse;
import com.dora.jagent.model.response.ResearchPaperChunkView;
import com.dora.jagent.model.response.ResearchPaperCollectionView;
import com.dora.jagent.service.PaperIndexService;
import com.dora.jagent.service.PaperResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/papers")
@RequiredArgsConstructor
public class PaperResearchController {

    private final PaperResearchService paperResearchService;
    private final PaperIndexService paperIndexService;

    @GetMapping("/search")
    public ApiResponse<PaperSearchResponse> searchPapers(
            @RequestParam String query,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String venue,
            @RequestParam(defaultValue = "2022") Integer fromYear,
            @RequestParam(required = false) Integer toYear,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(paperResearchService.searchPapers(query, scope, venue, fromYear, toYear, limit));
    }

    @GetMapping("/collections")
    public ApiResponse<List<ResearchPaperCollectionView>> getCollections(
            @RequestParam(defaultValue = "default") String collectionName
    ) {
        return ApiResponse.success(paperResearchService.getCollections(collectionName));
    }

    @PostMapping("/collections")
    public ApiResponse<ResearchPaperCollectionView> savePaper(
            @RequestBody SaveResearchPaperRequest request
    ) {
        return ApiResponse.success(paperResearchService.savePaper(request));
    }

    @PostMapping("/{paperId}/index")
    public ApiResponse<PaperIndexResponse> indexPaper(
            @PathVariable String paperId,
            @RequestParam(defaultValue = "default") String collectionName
    ) {
        return ApiResponse.success(paperIndexService.indexPaper(paperId, collectionName));
    }

    @PostMapping("/collections/{collectionName}/index")
    public ApiResponse<PaperIndexResponse> indexCollection(
            @PathVariable String collectionName
    ) {
        return ApiResponse.success(paperIndexService.indexCollection(collectionName));
    }

    @GetMapping("/semantic-search")
    public ApiResponse<List<ResearchPaperChunkView>> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "default") String collectionName,
            @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ApiResponse.success(paperIndexService.semanticSearch(query, collectionName, limit));
    }
}
