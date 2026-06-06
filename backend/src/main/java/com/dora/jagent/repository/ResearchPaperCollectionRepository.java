package com.dora.jagent.repository;

import com.dora.jagent.model.entity.ResearchPaperCollection;

import java.util.List;
import java.util.Optional;

public interface ResearchPaperCollectionRepository {

    List<ResearchPaperCollection> findByCollectionNameOrderByCreatedAtDesc(String collectionName);

    Optional<ResearchPaperCollection> findByPaperIdAndCollectionName(String paperId, String collectionName);

    ResearchPaperCollection save(ResearchPaperCollection collection);

    void updateStatus(String paperId, String collectionName, String status);
}
