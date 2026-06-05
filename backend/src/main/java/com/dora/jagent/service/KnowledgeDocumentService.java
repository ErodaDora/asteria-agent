package com.dora.jagent.service;

import com.dora.jagent.model.request.CreateKnowledgeDocumentRequest;
import com.dora.jagent.model.response.CreateKnowledgeDocumentResponse;
import com.dora.jagent.model.response.KnowledgeDocumentView;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {

    List<KnowledgeDocumentView> getDocumentsByKnowledgeBase(String knowledgeBaseId);

    KnowledgeDocumentView getDocument(String documentId);

    CreateKnowledgeDocumentResponse createDocument(String knowledgeBaseId, CreateKnowledgeDocumentRequest request);

    CreateKnowledgeDocumentResponse uploadDocument(String knowledgeBaseId, MultipartFile file);
}
