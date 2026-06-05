package com.dora.jagent.xhs.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.xhs.model.XhsGenerateRequest;
import com.dora.jagent.xhs.model.XhsGenerateResponse;
import com.dora.jagent.xhs.service.XhsGenerationWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/xhs/generate")
@RequiredArgsConstructor
public class XhsGenerationController {

    private final XhsGenerationWorkflowService xhsGenerationWorkflowService;

    @PostMapping("/run")
    public ApiResponse<XhsGenerateResponse> run(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsGenerateRequest request
    ) {
        return ApiResponse.success(xhsGenerationWorkflowService.generate(currentUserId, request));
    }

    @GetMapping("/latest")
    public ApiResponse<XhsGenerateResponse> latest(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsGenerationWorkflowService.getLatest(currentUserId));
    }
}
