package com.dora.jagent.service;

import com.dora.jagent.model.request.CreateKnowledgeBaseRequest;
import com.dora.jagent.model.response.CreateKnowledgeBaseResponse;
import com.dora.jagent.model.response.KnowledgeBaseView;

import java.util.List;

public interface KnowledgeBaseService {

    List<KnowledgeBaseView> getKnowledgeBases();

    KnowledgeBaseView getKnowledgeBase(String knowledgeBaseId);

    CreateKnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request);
}
