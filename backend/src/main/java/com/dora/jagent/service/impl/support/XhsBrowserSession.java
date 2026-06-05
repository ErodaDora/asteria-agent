package com.dora.jagent.service.impl.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class XhsBrowserSession implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private boolean detached;

    public void detach() {
        this.detached = true;
    }

    @Override
    public void close() {
        if (detached) {
            return;
        }
        try {
            context.close();
        } catch (Exception ignored) {
        }
        try {
            browser.close();
        } catch (Exception ignored) {
        }
        try {
            playwright.close();
        } catch (Exception ignored) {
        }
    }
}
