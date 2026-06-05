package com.dora.jagent.xhs.service;

import com.dora.jagent.xhs.model.XhsPublishRequest;
import com.dora.jagent.xhs.model.XhsPublishPreviewResponse;
import com.dora.jagent.xhs.model.XhsPublishResponse;

public interface XhsPublishService {

    XhsPublishPreviewResponse previewLatest(String userId, XhsPublishRequest request);

    XhsPublishResponse publishLatest(String userId, XhsPublishRequest request);
}
