package com.dora.jagent.service;

import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.xhs.model.XhsGenerateResponse;

public interface NotionSyncService {

    XhsNotionSyncResponse syncCrawlResult(XhsCrawlResponse response);

    XhsNotionSyncResponse syncGeneratedResult(XhsGenerateResponse generatedResponse, XhsCrawlResponse crawlResponse);
}
