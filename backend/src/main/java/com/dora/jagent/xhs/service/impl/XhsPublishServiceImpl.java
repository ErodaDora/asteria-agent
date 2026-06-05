package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.service.XhsBrowserSessionService;
import com.dora.jagent.service.XhsStorageService;
import com.dora.jagent.service.impl.support.XhsBrowserSession;
import com.dora.jagent.xhs.model.XhsContentItem;
import com.dora.jagent.xhs.model.XhsGenerateResponse;
import com.dora.jagent.xhs.model.XhsMcpPublishPayload;
import com.dora.jagent.xhs.model.XhsGeneratedTopicWithContents;
import com.dora.jagent.xhs.model.XhsPublishPreviewResponse;
import com.dora.jagent.xhs.model.XhsPublishRequest;
import com.dora.jagent.xhs.model.XhsPublishResponse;
import com.dora.jagent.xhs.service.XhsGenerationWorkflowService;
import com.dora.jagent.xhs.service.XhsMcpService;
import com.dora.jagent.xhs.service.XhsPublishService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class XhsPublishServiceImpl implements XhsPublishService {

    private static final String XHS_PUBLISH_URL = "https://creator.xiaohongshu.com/publish/publish";

    private final XhsGenerationWorkflowService xhsGenerationWorkflowService;
    private final XhsStorageService xhsStorageService;
    private final XhsBrowserSessionService xhsBrowserSessionService;
    private final XhsMcpService xhsMcpService;

    @Override
    public XhsPublishPreviewResponse previewLatest(String userId, XhsPublishRequest request) {
        PublishContext context = buildPublishContext(userId, request);
        XhsContentItem content = context.content();
        return XhsPublishPreviewResponse.builder()
                .datasetId(context.generated().getDatasetId())
                .topicIndex(context.topicIndex())
                .contentIndex(context.contentIndex())
                .title(truncate(defaultText(content.getTitle(), "小红书笔记"), 20))
                .body(defaultText(content.getBody(), ""))
                .cta(defaultText(content.getCta(), ""))
                .hashtags(cleanTags(content.getHashtags()))
                .coverImageUrl(context.sourceItem().getCoverImageUrl())
                .publishMode(useMcpMode(request) ? "mcp" : "playwright")
                .isOriginal(!Boolean.FALSE.equals(request.getIsOriginal()))
                .visibility(defaultText(request.getVisibility(), "公开可见"))
                .build();
    }

    @Override
    public XhsPublishResponse publishLatest(String userId, XhsPublishRequest request) {
        PublishContext context = buildPublishContext(userId, request);
        XhsContentItem content = context.content();
        XhsNoteItemView sourceItem = context.sourceItem();

        Path localImagePath = downloadCoverImage(sourceItem.getCoverImageUrl());
        if (useMcpMode(request)) {
            return publishViaMcp(content, sourceItem.getCoverImageUrl(), localImagePath, request);
        }

        try (XhsBrowserSession session = xhsBrowserSessionService.openSession(false)) {
            Page page = session.getPage();
            // Auto-accept any JS-level dialogs (alert/confirm) so they never block automation
            page.onDialog(dialog -> {
                try { dialog.accept(); } catch (Exception ignored) {}
            });
            page.navigate(XHS_PUBLISH_URL);
            page.waitForTimeout(4000);

            if (isCreatorLoginRequired(page)) {
                xhsBrowserSessionService.monitorExistingLoginSession(
                        session,
                        "当前创作者中心未登录，请在当前窗口完成登录后关闭窗口"
                );
                return XhsPublishResponse.builder()
                        .success(false)
                        .publishMode("playwright")
                        .loginRequired(true)
                        .manualActionRequired(false)
                        .message("当前创作者中心未登录，请在当前窗口完成登录后关闭窗口，再次点击发布")
                        .publishedTitle(truncate(content.getTitle(), 20))
                        .coverImageUrl(sourceItem.getCoverImageUrl())
                        .localImagePath(localImagePath.toAbsolutePath().toString())
                        .build();
            }

            try {
                fillPublishForm(page, content, localImagePath);
            } catch (BizException exception) {
                if (shouldKeepPageOpenForManualIntervention(exception)) {
                    session.detach();
                    return XhsPublishResponse.builder()
                            .success(false)
                            .publishMode("playwright")
                            .loginRequired(false)
                            .manualActionRequired(true)
                            .message(exception.getMessage() + " 当前发布页已保留，请手动切换到“上传图文”后继续操作。")
                            .publishedTitle(truncate(content.getTitle(), 20))
                            .coverImageUrl(sourceItem.getCoverImageUrl())
                            .localImagePath(localImagePath.toAbsolutePath().toString())
                            .build();
                }
                throw exception;
            }
            clickPublish(page);

            return XhsPublishResponse.builder()
                    .success(true)
                    .publishMode("playwright")
                    .loginRequired(false)
                    .manualActionRequired(false)
                    .message("已自动填写并点击发布，请以小红书页面实际结果为准")
                    .publishedTitle(truncate(content.getTitle(), 20))
                    .coverImageUrl(sourceItem.getCoverImageUrl())
                    .localImagePath(localImagePath.toAbsolutePath().toString())
                    .build();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("自动发布失败：" + exception.getMessage());
        }
    }

    private PublishContext buildPublishContext(String userId, XhsPublishRequest request) {
        XhsGenerateResponse generated = xhsGenerationWorkflowService.getLatest(userId);
        if (generated == null || generated.getResults() == null || generated.getResults().isEmpty()) {
            throw new BizException("当前没有可发布的生成结果，请先完成生成");
        }

        if (StringUtils.hasText(request.getDatasetId()) && !request.getDatasetId().equals(generated.getDatasetId())) {
            throw new BizException("datasetId 与最近一次生成结果不一致");
        }

        XhsCrawlResponse crawlResponse = xhsStorageService.getLatest(userId);
        if (crawlResponse == null || crawlResponse.getItems() == null || crawlResponse.getItems().isEmpty()) {
            throw new BizException("当前没有可用采集结果，暂时无法补图发布");
        }

        int topicIndex = safeIndex(request.getTopicIndex(), generated.getResults().size(), "topicIndex");
        XhsGeneratedTopicWithContents topicGroup = generated.getResults().get(topicIndex);
        List<XhsContentItem> contents = topicGroup.getContents() == null ? List.of() : topicGroup.getContents();
        if (contents.isEmpty()) {
            throw new BizException("当前选题下没有可发布文案");
        }
        int contentIndex = safeIndex(request.getContentIndex(), contents.size(), "contentIndex");
        XhsContentItem content = contents.get(contentIndex);

        XhsNoteItemView sourceItem = crawlResponse.getItems().stream()
                .filter(item -> StringUtils.hasText(item.getCoverImageUrl()))
                .findFirst()
                .orElseThrow(() -> new BizException("当前采集结果没有可用首图，暂时无法自动发布"));
        return new PublishContext(generated, topicIndex, contentIndex, content, sourceItem);
    }

    private XhsPublishResponse publishViaMcp(
            XhsContentItem content,
            String coverImageUrl,
            Path localImagePath,
            XhsPublishRequest request
    ) {
        Map<String, Object> result = xhsMcpService.publishContent(XhsMcpPublishPayload.builder()
                .Title(truncate(defaultText(content.getTitle(), "小红书笔记"), 20))
                .Content(buildPublishBody(content))
                .Images(List.of(localImagePath.toAbsolutePath().toString()))
                .Tags(cleanTags(content.getHashtags()))
                .IsOriginal(!Boolean.FALSE.equals(request.getIsOriginal()))
                .Visibility(defaultText(request.getVisibility(), "公开可见"))
                .build());

        boolean success = !Boolean.FALSE.equals(result.get("success"));
        String message = firstNonBlank(
                Objects.toString(result.get("message"), ""),
                Objects.toString(result.get("raw"), ""),
                success ? "已通过 MCP 服务提交小红书图文发布请求" : "小红书 MCP 发布失败"
        );

        return XhsPublishResponse.builder()
                .success(success)
                .publishMode("mcp")
                .loginRequired(false)
                .manualActionRequired(false)
                .message(message)
                .publishedTitle(truncate(content.getTitle(), 20))
                .coverImageUrl(coverImageUrl)
                .localImagePath(localImagePath.toAbsolutePath().toString())
                .build();
    }

    private boolean useMcpMode(XhsPublishRequest request) {
        return !"playwright".equalsIgnoreCase(defaultText(request.getMode(), "mcp"));
    }

    private List<String> cleanTags(List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return List.of();
        }
        return hashtags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.replaceFirst("^#+", "").trim())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(10)
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isCreatorLoginRequired(Page page) {
        String url = page.url() == null ? "" : page.url().toLowerCase();
        if (url.contains("login") || url.contains("signin")) {
            return true;
        }
        if (firstVisible(page, List.of("input[type='file']")) != null) {
            return false;
        }
        return firstVisible(page, List.of(
                "input[placeholder*='手机号']",
                "input[placeholder*='验证码']",
                "button:has-text('登录')",
                "text=请登录",
                "text=手机号登录"
        )) != null;
    }

    private void fillPublishForm(Page page, XhsContentItem content, Path imagePath) {
        ensureImageTabSelected(page);
        Locator uploadInput = resolveImageUploadInput(page);
        if (uploadInput == null) {
            throw new BizException("未找到小红书发布页的图片上传入口");
        }
        uploadInput.setInputFiles(imagePath);
        page.waitForTimeout(4000);

        failIfWrongTabToastVisible(page);

        fillFirstVisible(page, List.of(
                "input[placeholder*='标题']",
                "textarea[placeholder*='标题']"
        ), truncate(defaultText(content.getTitle(), "小红书笔记"), 20));

        String body = buildPublishBody(content);
        if (!tryFillContent(page, body)) {
            throw new BizException("未找到小红书发布页的正文输入区域");
        }
    }

    private void ensureImageTabSelected(Page page) {
        if (isImageMode(page)) {
            return;
        }

        for (int attempt = 0; attempt < 6; attempt++) {
            boolean clicked = (attempt % 2 == 0) ? tryClickImageTab(page) : tryClickImageTabViaJs(page);
            page.waitForTimeout(clicked ? 2000 : 800);
            if (isImageMode(page)) {
                return;
            }
        }

        throw new BizException("已尝试自动切换到“上传图文”，但仍未进入图文发布态");
    }

    private boolean tryClickImageTabViaJs(Page page) {
        try {
            String js = """
                    (() => {
                      const isVisible = (el) => !!el && el.offsetParent !== null;
                      const candidates = [
                        ...document.querySelectorAll("[role='tab'], button, li, a, span, div")
                      ];
                      for (const el of candidates) {
                        const text = (el.textContent || "").trim();
                        if (text !== "上传图文" || !isVisible(el)) {
                          continue;
                        }
                        const clickable = el.closest("[role='tab'], button, li, a, div, span") || el;
                        clickable.click();
                        return true;
                      }
                      return false;
                    })()
                    """;
            Object result = page.evaluate(js);
            return Boolean.TRUE.equals(result);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void failIfWrongTabToastVisible(Page page) {
        Locator toast = firstVisible(page, List.of(
                "text=上传图文，请先切换到图片tab",
                "text=请先切换到图片tab"
        ));
        if (toast != null) {
            throw new BizException("当前仍停留在视频发布模式，自动切换图文页失败，请先手动切换到“上传图文”后再试");
        }
    }

    private Locator resolveImageUploadInput(Page page) {
        try {
            Locator allInputs = page.locator("input[type='file']");
            int count = allInputs.count();
            if (count <= 0) {
                return null;
            }

            Locator fallback = null;
            for (int i = 0; i < count; i++) {
                Locator input = allInputs.nth(i);
                String accept = defaultText(input.getAttribute("accept"), "").toLowerCase();
                String multiple = defaultText(input.getAttribute("multiple"), "");
                boolean visible = isVisible(input);

                if (accept.contains("image")) {
                    if (visible) {
                        return input;
                    }
                    if (fallback == null) {
                        fallback = input;
                    }
                    continue;
                }

                if (visible && !accept.contains("video")) {
                    return input;
                }

                if (fallback == null && StringUtils.hasText(multiple)) {
                    fallback = input;
                }
            }
            return fallback != null ? fallback : allInputs.first();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean tryClickImageTab(Page page) {
        for (String selector : List.of(
                "button:has-text('上传图文')",
                "[role='tab']:has-text('上传图文')",
                ".tab:has-text('上传图文')",
                ".creator-tab:has-text('上传图文')",
                "text=上传图文"
        )) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0 && isVisible(locator)) {
                    locator.click(new Locator.ClickOptions().setForce(true));
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean isImageMode(Page page) {
        if (firstVisible(page, List.of(
                "input[placeholder*='标题']",
                "textarea[placeholder*='标题']",
                "text=拖拽图片到此处或点击上传",
                "button:has-text('上传图片')"
        )) != null) {
            return true;
        }
        return firstVisible(page, List.of(
                "text=拖拽视频到此处或点击上传",
                "button:has-text('上传视频')"
        )) == null && resolveImageUploadInput(page) != null;
    }

    private boolean shouldKeepPageOpenForManualIntervention(BizException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return message.contains("上传图文") || message.contains("图文发布态") || message.contains("图片上传入口");
    }

    private boolean tryFillContent(Page page, String body) {
        for (String selector : List.of(
                "div[contenteditable='true']",
                ".ql-editor",
                "textarea[placeholder*='正文']"
        )) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0 && isVisible(locator)) {
                    locator.click();
                    try {
                        locator.fill(body);
                    } catch (Exception ignored) {
                        page.keyboard().press("Meta+A");
                        page.keyboard().type(body);
                    }
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void clickPublish(Page page) {
        Locator publishButton = firstVisible(page, List.of(
                "button:has-text('发布')",
                "button:has-text('立即发布')"
        ));
        if (publishButton == null) {
            throw new BizException("未找到小红书发布按钮");
        }
        publishButton.click();
        page.waitForTimeout(3000);
    }

    private Locator firstVisible(Page page, List<String> selectors) {
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0 && isVisible(locator)) {
                    return locator;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isVisible(Locator locator) {
        try {
            return locator != null && locator.isVisible();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void fillFirstVisible(Page page, List<String> selectors, String value) {
        Locator locator = firstVisible(page, selectors);
        if (locator == null) {
            throw new BizException("未找到发布表单输入框");
        }
        locator.fill(value);
    }

    private Path downloadCoverImage(String imageUrl) {
        try {
            Path dir = Path.of("./data/xhs/publish").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve("cover-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".jpg");
            URLConnection connection = URI.create(imageUrl).toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception exception) {
            throw new BizException("下载首图失败：" + exception.getMessage());
        }
    }

    private String buildPublishBody(XhsContentItem content) {
        String body = defaultText(content.getBody(), "");
        String cta = defaultText(content.getCta(), "");
        String tags = content.getHashtags() == null ? "" : String.join(" ", content.getHashtags());
        return String.join("\n\n", List.of(body, cta, tags).stream().filter(StringUtils::hasText).toList());
    }

    private int safeIndex(Integer index, int size, String field) {
        int value = index == null ? 0 : index;
        if (value < 0 || value >= size) {
            throw new BizException(field + " 超出范围");
        }
        return value;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > limit ? trimmed.substring(0, limit) : trimmed;
    }

    private record PublishContext(
            XhsGenerateResponse generated,
            int topicIndex,
            int contentIndex,
            XhsContentItem content,
            XhsNoteItemView sourceItem
    ) {
    }
}
