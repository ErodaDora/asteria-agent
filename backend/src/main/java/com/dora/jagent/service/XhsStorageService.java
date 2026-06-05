package com.dora.jagent.service;

import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNotionSyncResponse;

public interface XhsStorageService {

    XhsCrawlResponse saveLatest(String userId, XhsCrawlResponse response);

    XhsCrawlResponse getLatest(String userId);

    XhsNotionSyncResponse syncLatestToNotion(String userId, String datasetId);
}
