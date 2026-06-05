package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.CreateKnowledgeDocumentRequest;
import com.dora.jagent.model.response.CreateKnowledgeDocumentResponse;
import com.dora.jagent.model.response.KnowledgeDocumentView;
import com.dora.jagent.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping
    public ApiResponse<List<KnowledgeDocumentView>> getDocumentsByKnowledgeBase(
            @PathVariable String knowledgeBaseId
    ) {
        return ApiResponse.success(knowledgeDocumentService.getDocumentsByKnowledgeBase(knowledgeBaseId));
    }

    @PostMapping
    public ApiResponse<CreateKnowledgeDocumentResponse> createDocument(
            @PathVariable String knowledgeBaseId,
            @RequestBody CreateKnowledgeDocumentRequest request
    ) {
        return ApiResponse.success(knowledgeDocumentService.createDocument(knowledgeBaseId, request));
    }

    @PostMapping("/upload")
    public ApiResponse<CreateKnowledgeDocumentResponse> uploadDocument(
            @PathVariable String knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(knowledgeDocumentService.uploadDocument(knowledgeBaseId, file));
    }
}
