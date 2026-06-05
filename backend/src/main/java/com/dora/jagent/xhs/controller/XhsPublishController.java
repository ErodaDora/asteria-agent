package com.dora.jagent.xhs.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.XhsNotionSyncRequest;
import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.xhs.model.XhsPublishPreviewResponse;
import com.dora.jagent.xhs.model.XhsPublishRequest;
import com.dora.jagent.xhs.model.XhsPublishResponse;
import com.dora.jagent.xhs.service.XhsGenerationWorkflowService;
import com.dora.jagent.xhs.service.XhsPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/xhs/generated")
@RequiredArgsConstructor
public class XhsPublishController {

    private final XhsGenerationWorkflowService xhsGenerationWorkflowService;
    private final XhsPublishService xhsPublishService;

    @PostMapping("/notion/sync")
    public ApiResponse<XhsNotionSyncResponse> syncGeneratedToNotion(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsNotionSyncRequest request
    ) {
        return ApiResponse.success(xhsGenerationWorkflowService.syncLatestToNotion(currentUserId, request.getDatasetId()));
    }

    @PostMapping("/publish")
    public ApiResponse<XhsPublishResponse> publishGenerated(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsPublishRequest request
    ) {
        return ApiResponse.success(xhsPublishService.publishLatest(currentUserId, request));
    }

    @PostMapping("/publish/preview")
    public ApiResponse<XhsPublishPreviewResponse> previewGenerated(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsPublishRequest request
    ) {
        return ApiResponse.success(xhsPublishService.previewLatest(currentUserId, request));
    }
}
