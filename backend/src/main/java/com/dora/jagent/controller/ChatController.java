package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.RenameSessionRequest;
import com.dora.jagent.model.request.SimpleChatRequest;
import com.dora.jagent.model.response.ChatMessageView;
import com.dora.jagent.model.response.ChatSessionView;
import com.dora.jagent.model.response.SimpleChatResponse;
import com.dora.jagent.service.ChatService;
import com.dora.jagent.service.impl.SpringAiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SpringAiChatService springAiChatService;

    // 当前这是登录后的最小聊天入口。
    // 只要求 access token 已经过拦截器校验通过。
    @PostMapping("/simple")
    public ApiResponse<SimpleChatResponse> simpleChat(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody SimpleChatRequest request
    ) {
        return ApiResponse.success(chatService.chat(currentUserId, request.getSessionId(), request.getMessage()));
    }

    // 这条接口专门用来展示 Spring AI 的 ChatClient 调法。
    // 它保留和 /simple 一样的 session / summary / message 策略，
    // 只把“调模型”的实现替换成 ChatClient。
    @PostMapping("/spring-ai")
    public ApiResponse<SimpleChatResponse> springAiChat(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody SimpleChatRequest request
    ) {
        return ApiResponse.success(
                springAiChatService.chat(
                        currentUserId,
                        request.getSessionId(),
                        request.getModelKey(),
                        request.getMessage()
                )
        );
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageView>> getSessionMessages(
            @RequestAttribute("currentUserId") String currentUserId,
            @PathVariable String sessionId
    ) {
        return ApiResponse.success(chatService.getSessionMessages(currentUserId, sessionId));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionView>> getSessions(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(chatService.getSessions(currentUserId));
    }

    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionView> renameSession(
            @RequestAttribute("currentUserId") String currentUserId,
            @PathVariable String sessionId,
            @RequestBody RenameSessionRequest request
    ) {
        return ApiResponse.success(chatService.renameSession(currentUserId, sessionId, request.getTitle()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @RequestAttribute("currentUserId") String currentUserId,
            @PathVariable String sessionId
    ) {
        chatService.deleteSession(currentUserId, sessionId);
        return ApiResponse.success("session deleted", null);
    }
}
