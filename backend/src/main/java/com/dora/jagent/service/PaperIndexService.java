package com.dora.jagent.service;

import com.dora.jagent.model.response.PaperIndexResponse;
import com.dora.jagent.model.response.ResearchPaperChunkView;

import java.util.List;

public interface PaperIndexService {

    PaperIndexResponse indexPaper(String paperId, String collectionName);

    PaperIndexResponse indexCollection(String collectionName);

    List<ResearchPaperChunkView> semanticSearch(String query, String collectionName, Integer limit);
}
