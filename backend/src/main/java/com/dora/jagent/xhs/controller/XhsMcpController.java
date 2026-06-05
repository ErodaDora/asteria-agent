package com.dora.jagent.xhs.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.xhs.model.XhsMcpPublishPayload;
import com.dora.jagent.xhs.model.XhsMcpServiceStatusResponse;
import com.dora.jagent.xhs.model.XhsMcpToolCallRequest;
import com.dora.jagent.xhs.service.XhsMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/xhs/mcp")
@RequiredArgsConstructor
public class XhsMcpController {

    private final XhsMcpService xhsMcpService;

    @GetMapping("/service/status")
    public ApiResponse<XhsMcpServiceStatusResponse> getServiceStatus(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsMcpService.getServiceStatus());
    }

    @PostMapping("/service/start")
    public ApiResponse<XhsMcpServiceStatusResponse> startService(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestParam(name = "headless", defaultValue = "true") boolean headless
    ) {
        return ApiResponse.success(xhsMcpService.startService(headless));
    }

    @PostMapping("/service/stop")
    public ApiResponse<XhsMcpServiceStatusResponse> stopService(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsMcpService.stopService());
    }

    @PostMapping("/service/login")
    public ApiResponse<Map<String, Object>> runLogin(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsMcpService.runLogin());
    }

    @GetMapping("/login/status")
    public ApiResponse<Map<String, Object>> checkLoginStatus(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsMcpService.checkLoginStatus());
    }

    @GetMapping("/tools")
    public ApiResponse<List<String>> listTools(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsMcpService.listTools());
    }

    @GetMapping("/feeds")
    public ApiResponse<Map<String, Object>> listFeeds(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(xhsMcpService.listFeeds(limit == null ? Map.of() : Map.of("limit", limit)));
    }

    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> searchFeeds(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody(required = false) Map<String, Object> arguments
    ) {
        return ApiResponse.success(xhsMcpService.searchFeeds(arguments == null ? Map.of() : arguments));
    }

    @PostMapping("/publish/content")
    public ApiResponse<Map<String, Object>> publishContent(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsMcpPublishPayload payload
    ) {
        return ApiResponse.success(xhsMcpService.publishContent(payload));
    }

    @PostMapping("/publish/video")
    public ApiResponse<Map<String, Object>> publishVideo(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody(required = false) Map<String, Object> arguments
    ) {
        return ApiResponse.success(xhsMcpService.publishVideo(arguments == null ? Map.of() : arguments));
    }

    @PostMapping("/tool/call")
    public ApiResponse<Map<String, Object>> callTool(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsMcpToolCallRequest request
    ) {
        return ApiResponse.success(xhsMcpService.callTool(request.getToolName(), request.getArguments()));
    }
}
