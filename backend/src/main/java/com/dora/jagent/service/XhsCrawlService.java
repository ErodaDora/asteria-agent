package com.dora.jagent.service;

import com.dora.jagent.model.request.XhsCrawlRequest;
import com.dora.jagent.model.response.XhsCrawlResponse;

public interface XhsCrawlService {

    XhsCrawlResponse crawl(String userId, XhsCrawlRequest request);

    XhsCrawlResponse getLatest(String userId);
}
