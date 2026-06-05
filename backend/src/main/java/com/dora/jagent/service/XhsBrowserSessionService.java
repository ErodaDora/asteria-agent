package com.dora.jagent.service;

import com.dora.jagent.model.response.XhsLoginStatusResponse;
import com.dora.jagent.service.impl.support.XhsBrowserSession;

public interface XhsBrowserSessionService {

    XhsLoginStatusResponse startLoginSession();

    XhsLoginStatusResponse startCreatorLoginSession();

    XhsLoginStatusResponse getLoginStatus();

    boolean hasStoredLoginState();

    XhsBrowserSession openSession(boolean headless);

    void monitorExistingLoginSession(XhsBrowserSession session, String openedMessage);
}
