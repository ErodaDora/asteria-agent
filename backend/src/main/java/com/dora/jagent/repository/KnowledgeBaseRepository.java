package com.dora.jagent.repository;

import com.dora.jagent.model.entity.KnowledgeBase;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {

    List<KnowledgeBase> findAllOrderByCreatedAtAsc();

    Optional<KnowledgeBase> findById(String knowledgeBaseId);

    KnowledgeBase save(KnowledgeBase knowledgeBase);
}
