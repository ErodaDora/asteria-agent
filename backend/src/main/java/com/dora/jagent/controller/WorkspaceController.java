package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.response.WorkspaceLolEsportsResponse;
import com.dora.jagent.model.response.WorkspaceNoteSyncResponse;
import com.dora.jagent.model.response.WorkspaceWeatherResponse;
import com.dora.jagent.service.WorkspaceService;
import com.dora.jagent.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping("/weather/today")
    public ApiResponse<WorkspaceWeatherResponse> getTodayWeather(
            @RequestAttribute("currentUserId") String currentUserId,
            HttpServletRequest request
    ) {
        String clientIp = ClientIpUtils.resolveClientIp(request);
        return ApiResponse.success(workspaceService.getTodayWeatherCard(clientIp));
    }

    @GetMapping("/lol-esports/today")
    public ApiResponse<WorkspaceLolEsportsResponse> getTodayLolEsports(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(workspaceService.getTodayLolEsportsCard());
    }

    @GetMapping("/notes/summary")
    public ApiResponse<WorkspaceNoteSyncResponse> getNotesSummary(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(workspaceService.getNoteSyncCard());
    }

    @PostMapping("/notes/sync")
    public ApiResponse<WorkspaceNoteSyncResponse> syncNotes(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(workspaceService.syncNotesToNotion());
    }
}
