package com.dora.jagent.xhs.service;

import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.xhs.model.XhsGenerateRequest;
import com.dora.jagent.xhs.model.XhsGenerateResponse;

public interface XhsGenerationWorkflowService {

    XhsGenerateResponse generate(String userId, XhsGenerateRequest request);

    XhsGenerateResponse getLatest(String userId);

    XhsNotionSyncResponse syncLatestToNotion(String userId, String datasetId);
}
