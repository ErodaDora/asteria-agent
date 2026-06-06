package com.dora.jagent.repository;

import com.dora.jagent.model.entity.ResearchPaper;

import java.util.Optional;

public interface ResearchPaperRepository {

    Optional<ResearchPaper> findById(String paperId);

    Optional<ResearchPaper> findByOpenalexId(String openalexId);

    Optional<ResearchPaper> findByDoi(String doi);

    ResearchPaper save(ResearchPaper paper);
}
