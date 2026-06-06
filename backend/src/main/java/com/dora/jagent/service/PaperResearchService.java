package com.dora.jagent.service;

import com.dora.jagent.model.request.SaveResearchPaperRequest;
import com.dora.jagent.model.response.PaperSearchResponse;
import com.dora.jagent.model.response.ResearchPaperCollectionView;

import java.util.List;

public interface PaperResearchService {

    PaperSearchResponse searchPapers(
            String query,
            String scope,
            String venue,
            Integer fromYear,
            Integer toYear,
            Integer limit
    );

    ResearchPaperCollectionView savePaper(SaveResearchPaperRequest request);

    List<ResearchPaperCollectionView> getCollections(String collectionName);
}
