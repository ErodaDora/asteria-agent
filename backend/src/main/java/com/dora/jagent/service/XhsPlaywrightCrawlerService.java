package com.dora.jagent.service;

import com.dora.jagent.model.request.XhsCrawlRequest;
import com.dora.jagent.model.response.XhsNoteItemView;

import java.util.List;

public interface XhsPlaywrightCrawlerService {

    List<XhsNoteItemView> crawl(XhsCrawlRequest request);
}
