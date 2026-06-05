package com.dora.jagent.service.impl;

import com.dora.jagent.model.response.XhsLoginStatusResponse;
import com.dora.jagent.service.XhsBrowserSessionService;
import com.dora.jagent.service.impl.support.XhsBrowserSession;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class XhsBrowserSessionServiceImpl implements XhsBrowserSessionService {

    private static final String XHS_HOME = "https://www.xiaohongshu.com";
    private static final String XHS_CREATOR_PUBLISH_URL = "https://creator.xiaohongshu.com/publish/publish";
    private static final String XHS_CREATOR_HOME = "https://creator.xiaohongshu.com/";

    private final Path storageStatePath;
    private final int loginWaitSeconds;
    private final AtomicBoolean loginWindowRunning = new AtomicBoolean(false);
    private final AtomicReference<String> lastMessage = new AtomicReference<>("尚未初始化登录态");

    public XhsBrowserSessionServiceImpl(
            @Value("${xhs.playwright.storage-state-path:./data/xhs/xhs_state.json}") String storageStatePath,
            @Value("${xhs.playwright.login-wait-seconds:120}") int loginWaitSeconds
    ) {
        this.storageStatePath = Path.of(storageStatePath).toAbsolutePath().normalize();
        this.loginWaitSeconds = loginWaitSeconds;
    }

    @Override
    public XhsLoginStatusResponse startLoginSession() {
        return startInteractiveLoginSession(XHS_HOME, false, "登录窗口已弹出，请在浏览器中完成小红书登录");
    }

    @Override
    public XhsLoginStatusResponse startCreatorLoginSession() {
        return startInteractiveLoginSession(XHS_CREATOR_HOME, true, "创作者登录窗口已弹出，请在浏览器中完成登录后关闭窗口");
    }

    private XhsLoginStatusResponse startInteractiveLoginSession(String targetUrl, boolean preserveExistingState, String openedMessage) {
        if (loginWindowRunning.get()) {
            return buildStatus("登录窗口已在运行，请先在浏览器中完成登录");
        }

        loginWindowRunning.set(true);
        lastMessage.set("正在启动 Playwright 浏览器...");

        Thread loginThread = new Thread(() -> {
            try {
                Files.createDirectories(storageStatePath.getParent());
                if (!preserveExistingState) {
                    Files.deleteIfExists(storageStatePath);
                }

                try (Playwright playwright = Playwright.create()) {
                    lastMessage.set("Playwright 已初始化，正在启动 Chromium...");
                    Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
                    lastMessage.set("Chromium 已启动，正在打开目标页面...");
                    Browser.NewContextOptions options = new Browser.NewContextOptions()
                            .setViewportSize(1440, 900)
                            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
                    if (preserveExistingState && Files.exists(storageStatePath)) {
                        options.setStorageStatePath(storageStatePath);
                    }
                    BrowserContext context = browser.newContext(options);
                    Page page = context.newPage();
                    page.navigate(targetUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                    lastMessage.set(openedMessage);

                    boolean hasSnapshot = false;
                    while (true) {
                        if (!page.isClosed()) {
                            hasSnapshot = tryPersistStorageState(context) || hasSnapshot;
                        }
                        if (page.isClosed()) {
                            lastMessage.set(hasSnapshot
                                    ? "检测到你已关闭登录窗口，最近一次浏览器状态已保存。请再次点击开始检索。"
                                    : "登录窗口已关闭，但本次还没来得及保存有效登录态。请重试一次。");
                            break;
                        }
                        page.waitForTimeout(1000);
                    }

                    context.close();
                    browser.close();
                }

                lastMessage.set(Files.exists(storageStatePath)
                        ? "登录态已保存，可开始采集"
                        : "登录窗口已关闭，但未检测到已保存的登录态");
            } catch (Exception e) {
                lastMessage.set("Playwright 启动/保存失败：" + e.getMessage());
            } finally {
                loginWindowRunning.set(false);
            }
        }, "xhs-login-session-thread");
        loginThread.setDaemon(true);
        loginThread.start();

        return buildStatus(lastMessage.get());
    }

    @Override
    public XhsLoginStatusResponse getLoginStatus() {
        return buildStatus(lastMessage.get());
    }

    @Override
    public boolean hasStoredLoginState() {
        return Files.exists(storageStatePath);
    }

    @Override
    public XhsBrowserSession openSession(boolean headless) {
        try {
            Files.createDirectories(storageStatePath.getParent());
        } catch (Exception e) {
            throw new IllegalStateException("创建 XHS 登录态目录失败：" + e.getMessage(), e);
        }

        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(1440, 900)
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        if (Files.exists(storageStatePath)) {
            options.setStorageStatePath(storageStatePath);
        }
        BrowserContext context = browser.newContext(options);
        // Pre-grant permissions so the browser never shows an OS-level dialog during automation
        try {
            context.grantPermissions(List.of("camera", "microphone", "notifications"));
        } catch (Exception ignored) {}
        Page page = context.newPage();
        return new XhsBrowserSession(playwright, browser, context, page);
    }

    @Override
    public void monitorExistingLoginSession(XhsBrowserSession session, String openedMessage) {
        if (session == null || session.getPage() == null) {
            return;
        }
        if (loginWindowRunning.get()) {
            return;
        }

        session.detach();
        loginWindowRunning.set(true);
        lastMessage.set(openedMessage);

        Thread loginThread = new Thread(() -> {
            try {
                Files.createDirectories(storageStatePath.getParent());
                Page page = session.getPage();
                BrowserContext context = session.getContext();
                boolean hasSnapshot = false;
                while (true) {
                    if (!page.isClosed()) {
                        hasSnapshot = tryPersistStorageState(context) || hasSnapshot;
                    }
                    if (page.isClosed()) {
                        lastMessage.set(hasSnapshot
                                ? "检测到你已关闭创作者登录窗口，最近一次浏览器状态已保存。请再次点击发布。"
                                : "创作者登录窗口已关闭，但还没保存到有效登录态。请重试一次。");
                        break;
                    }
                    page.waitForTimeout(1000);
                }
                session.getContext().close();
                session.getBrowser().close();
                session.getPlaywright().close();
                lastMessage.set(Files.exists(storageStatePath)
                        ? "创作者登录态已保存，可再次点击发布"
                        : "创作者登录窗口已关闭，但未检测到已保存的登录态");
            } catch (Exception e) {
                lastMessage.set("创作者登录态保存失败：" + e.getMessage());
            } finally {
                loginWindowRunning.set(false);
            }
        }, "xhs-existing-login-session-thread");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private XhsLoginStatusResponse buildStatus(String message) {
        return XhsLoginStatusResponse.builder()
                .loggedIn(Files.exists(storageStatePath))
                .loginWindowRunning(loginWindowRunning.get())
                .storageStatePath(storageStatePath.toString())
                .message(message)
                .build();
    }

    private boolean tryPersistStorageState(BrowserContext context) {
        try {
            context.storageState(new BrowserContext.StorageStateOptions().setPath(storageStatePath));
            return Files.exists(storageStatePath);
        } catch (Exception ignored) {
            return false;
        }
    }
}
