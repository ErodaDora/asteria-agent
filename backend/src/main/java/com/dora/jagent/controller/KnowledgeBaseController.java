package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.CreateKnowledgeBaseRequest;
import com.dora.jagent.model.response.CreateKnowledgeBaseResponse;
import com.dora.jagent.model.response.KnowledgeBaseView;
import com.dora.jagent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public ApiResponse<List<KnowledgeBaseView>> getKnowledgeBases() {
        return ApiResponse.success(knowledgeBaseService.getKnowledgeBases());
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseView> getKnowledgeBase(@PathVariable String knowledgeBaseId) {
        return ApiResponse.success(knowledgeBaseService.getKnowledgeBase(knowledgeBaseId));
    }

    @PostMapping
    public ApiResponse<CreateKnowledgeBaseResponse> createKnowledgeBase(
            @RequestBody CreateKnowledgeBaseRequest request
    ) {
        return ApiResponse.success(knowledgeBaseService.createKnowledgeBase(request));
    }
}
