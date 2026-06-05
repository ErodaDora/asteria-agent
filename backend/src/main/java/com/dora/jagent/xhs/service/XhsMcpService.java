package com.dora.jagent.xhs.service;

import com.dora.jagent.xhs.model.XhsMcpPublishPayload;
import com.dora.jagent.xhs.model.XhsMcpServiceStatusResponse;

import java.util.List;
import java.util.Map;

public interface XhsMcpService {

    XhsMcpServiceStatusResponse getServiceStatus();

    XhsMcpServiceStatusResponse startService(boolean headless);

    XhsMcpServiceStatusResponse stopService();

    Map<String, Object> runLogin();

    Map<String, Object> checkLoginStatus();

    List<String> listTools();

    Map<String, Object> listFeeds(Map<String, Object> arguments);

    Map<String, Object> searchFeeds(Map<String, Object> arguments);

    Map<String, Object> publishContent(XhsMcpPublishPayload payload);

    Map<String, Object> publishVideo(Map<String, Object> arguments);

    Map<String, Object> callTool(String toolName, Map<String, Object> arguments);
}
