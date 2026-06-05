package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.AgentChatRequest;
import com.dora.jagent.model.request.CreateAgentRequest;
import com.dora.jagent.model.request.UpdateAgentKnowledgeBasesRequest;
import com.dora.jagent.model.response.AgentView;
import com.dora.jagent.model.response.ChatMessageView;
import com.dora.jagent.model.response.ChatSessionView;
import com.dora.jagent.model.response.SimpleChatResponse;
import com.dora.jagent.service.AgentChatService;
import com.dora.jagent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentChatService agentChatService;

    @GetMapping
    public ApiResponse<List<AgentView>> getAgents() {
        return ApiResponse.success(agentService.getAgents());
    }

    @PostMapping
    public ApiResponse<AgentView> createAgent(@RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agentService.createAgent(request));
    }

    @PatchMapping("/{agentId}/knowledge-bases")
    public ApiResponse<AgentView> updateAllowedKnowledgeBases(
            @PathVariable String agentId,
            @RequestBody UpdateAgentKnowledgeBasesRequest request
    ) {
        return ApiResponse.success(agentService.updateAllowedKnowledgeBases(agentId, request));
    }

    @GetMapping("/{agentId}/sessions")
    public ApiResponse<List<ChatSessionView>> getAgentSessions(
            @RequestAttribute("currentUserId") String currentUserId,
            @PathVariable String agentId
    ) {
        return ApiResponse.success(agentChatService.getSessions(currentUserId, agentId));
    }

    @GetMapping("/{agentId}/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageView>> getAgentSessionMessages(
            @RequestAttribute("currentUserId") String currentUserId,
            @PathVariable String agentId,
            @PathVariable String sessionId
    ) {
        return ApiResponse.success(agentChatService.getSessionMessages(currentUserId, agentId, sessionId));
    }

    @PostMapping("/{agentId}/chat")
    public ApiResponse<SimpleChatResponse> agentChat(
            @RequestAttribute("currentUserId") String currentUserId,
            @PathVariable String agentId,
            @RequestBody AgentChatRequest request
    ) {
        return ApiResponse.success(
                agentChatService.chat(currentUserId, agentId, request.getSessionId(), request.getMessage())
        );
    }
}
