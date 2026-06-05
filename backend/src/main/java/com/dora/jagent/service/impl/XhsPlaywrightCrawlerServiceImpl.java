package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.request.XhsCrawlRequest;
import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.service.XhsBrowserSessionService;
import com.dora.jagent.service.XhsPlaywrightCrawlerService;
import com.dora.jagent.service.impl.support.XhsBrowserSession;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class XhsPlaywrightCrawlerServiceImpl implements XhsPlaywrightCrawlerService {

    private static final String XHS_BASE = "https://www.xiaohongshu.com";
    private static final List<String> INVALID_AD_WORDS = List.of("商单", "广告", "投放合作", "品牌合作");
    private static final List<String> VIDEO_TYPE_KEYWORDS = List.of("视频", "直播", "合集");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");
    private static final Pattern RELATIVE_DAY_PATTERN = Pattern.compile("(\\d+)天前");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})-(\\d{1,2})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final XhsBrowserSessionService xhsBrowserSessionService;
    private final boolean defaultHeadless;

    public XhsPlaywrightCrawlerServiceImpl(
            XhsBrowserSessionService xhsBrowserSessionService,
            @Value("${xhs.playwright.headless:false}") boolean defaultHeadless
    ) {
        this.xhsBrowserSessionService = xhsBrowserSessionService;
        this.defaultHeadless = defaultHeadless;
    }

    @Override
    public List<XhsNoteItemView> crawl(XhsCrawlRequest request) {
        if (!xhsBrowserSessionService.hasStoredLoginState()) {
            throw new BizException("当前还没有可用的小红书登录态，请先点击“启动登录态”完成一次登录");
        }

        List<String> keywords = request.getKeywords() == null ? List.of() : request.getKeywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        try (XhsBrowserSession session = xhsBrowserSessionService.openSession(defaultHeadless)) {
            Page page = session.getPage();
            List<XhsNoteItemView> collected = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            List<String> topicWords = request.getTopicWords() == null ? List.of() : request.getTopicWords();

            for (String keyword : keywords) {
                if (collected.size() >= safeTargetCount(request)) {
                    break;
                }
                List<SearchCard> cards = collectCardLinks(page, keyword);
                for (SearchCard card : cards) {
                    if (collected.size() >= safeTargetCount(request) || seenUrls.contains(card.url())) {
                        continue;
                    }
                    XhsNoteItemView note = fetchDetail(page, card, topicWords);
                    if (note != null && isValid(note, request, topicWords)) {
                        collected.add(note);
                        seenUrls.add(card.url());
                    }
                }
            }
            return collected.stream()
                    .sorted(Comparator.comparing(XhsNoteItemView::getLikes, Comparator.nullsLast(Integer::compareTo)).reversed())
                    .toList();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Playwright 抓取失败：" + e.getMessage());
        }
    }

    private List<SearchCard> collectCardLinks(Page page, String keyword) {
        String searchUrl = XHS_BASE + "/search_result?keyword="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&source=web_explore_feed";
        page.navigate(searchUrl);
        page.waitForTimeout(2500);
        dismissPopups(page);
        scrollPage(page, 2);

        Locator cards = page.locator("section.note-item");
        int count = Math.min((int) cards.count(), 80);
        List<SearchCard> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Locator card = cards.nth(i);
            if (isVideoCard(card)) {
                continue;
            }
            String href = safeAttribute(card.locator("a.cover").first(), "href");
            if (!StringUtils.hasText(href)) {
                continue;
            }
            String url = href.startsWith("http") ? href : XHS_BASE + href;
            String cardTitle = firstNonBlank(
                    safeText(card.locator(".title").first()),
                    safeText(card.locator("[class*='title']").first())
            );
            String authorRaw = safeText(card.locator(".author").first());
            String author = StringUtils.hasText(authorRaw) ? authorRaw.split("\n")[0].trim() : "";
            String dateText = safeText(card.locator(".time").first());
            String normalizedDate = normalizeDate(dateText);
            result.add(new SearchCard(url, author, normalizedDate, keyword, cardTitle));
        }
        return result;
    }

    private XhsNoteItemView fetchDetail(Page page, SearchCard card, List<String> topicWords) {
        try {
            page.navigate(card.url());
            page.waitForTimeout(1600);
            dismissPopups(page);

            String title = resolveDetailTitle(page, card);
            String content = safeText(page.locator("#detail-desc").first());
            String publishTime = firstNonBlank(
                    safeText(page.locator(".bottom-container .date").first()),
                    safeText(page.locator(".note-header .date").first()),
                    safeText(page.locator(".date").first()),
                    card.cardDate()
            );
            String coverImageUrl = resolveCoverImage(page);
            List<String> hotComments = resolveHotComments(page);

            List<String> tags = firstAvailableTags(page);

            return XhsNoteItemView.builder()
                    .title(title)
                    .content(content)
                    .author(card.author())
                    .comments(parseNumber(safeMetaContent(page, "meta[name='og:xhs:note_comment']")))
                    .likes(parseNumber(safeMetaContent(page, "meta[name='og:xhs:note_like']")))
                    .favorites(parseNumber(safeMetaContent(page, "meta[name='og:xhs:note_collect']")))
                    .tags(tags)
                    .publishTime(normalizeDate(publishTime))
                    .url(card.url())
                    .coverImageUrl(coverImageUrl)
                    .hotComments(hotComments)
                    .contentType("图文")
                    .keywordUsed(card.keyword())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveDetailTitle(Page page, SearchCard card) {
        String domTitle = firstNonBlank(
                safeText(page.locator("#detail-title").first()),
                safeText(page.locator(".note-content .title").first()),
                safeText(page.locator(".note-title").first()),
                safeText(page.locator("h1").first())
        );
        if (looksLikeUsableTitle(domTitle)) {
            return domTitle;
        }

        String cardTitle = card.cardTitle();
        if (looksLikeUsableTitle(cardTitle)) {
            return cardTitle;
        }

        String rawTitle = page.title();
        String browserTitle = rawTitle == null ? "" : rawTitle.replaceAll("\\s*[-–—]\\s*小红书.*$", "").trim();
        if (looksLikeUsableTitle(browserTitle)) {
            return browserTitle;
        }

        return card.keyword();
    }

    private boolean looksLikeUsableTitle(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String cleaned = text.trim().replaceAll("\\s+", " ");
        if (cleaned.length() > 60) {
            return false;
        }
        return cleaned.chars().filter(ch -> ch == '\n').count() == 0;
    }

    private boolean isValid(XhsNoteItemView note, XhsCrawlRequest request, List<String> topicWords) {
        if (!StringUtils.hasText(note.getContent())) {
            return false;
        }

        String fullText = (note.getTitle() + " " + note.getContent()).trim();
        if (INVALID_AD_WORDS.stream().anyMatch(fullText::contains)) {
            return false;
        }
        if (!topicWords.isEmpty() && topicWords.stream().noneMatch(word -> StringUtils.hasText(word) && fullText.contains(word.trim()))) {
            return false;
        }
        if (safeInt(note.getComments()) < safeInt(request.getMinComments())) {
            return false;
        }
        if (safeInt(note.getLikes()) < safeInt(request.getMinLikes())) {
            return false;
        }
        if (safeInt(note.getFavorites()) < safeInt(request.getMinFavorites())) {
            return false;
        }
        return true;
    }

    private String resolveCoverImage(Page page) {
        String metaImage = safeMetaContent(page, "meta[name='og:image']");
        if (StringUtils.hasText(metaImage)) {
            return metaImage;
        }
        for (String selector : List.of(
                ".swiper-slide img",
                ".note-slider-box img",
                ".carousel-container img",
                ".note-content img"
        )) {
            try {
                Locator locator = page.locator(selector).first();
                String src = firstNonBlank(
                        safeAttribute(locator, "src"),
                        safeAttribute(locator, "data-src"),
                        safeAttribute(locator, "style")
                );
                if (StringUtils.hasText(src)) {
                    return normalizeImageUrl(src);
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private List<String> resolveHotComments(Page page) {
        try {
            page.evaluate("window.scrollBy(0, 1200)");
            page.waitForTimeout(1200);
        } catch (Exception ignored) {
        }

        List<CommentCandidate> comments = new ArrayList<>();
        for (String selector : List.of(".comment-item", "[class*='comment-item']", ".parent-comment-item")) {
            try {
                Locator items = page.locator(selector);
                int count = Math.min((int) items.count(), 12);
                if (count == 0) {
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    Locator item = items.nth(i);
                    String text = firstNonBlank(
                            safeText(item.locator(".content").first()),
                            safeText(item.locator(".comment-content").first()),
                            safeText(item.locator(".note-text").first()),
                            safeText(item)
                    );
                    if (!StringUtils.hasText(text)) {
                        continue;
                    }
                    String user = firstNonBlank(
                            safeText(item.locator(".name").first()),
                            safeText(item.locator(".author").first()),
                            safeText(item.locator("[class*='user']").first())
                    );
                    int likes = parseNumber(firstNonBlank(
                            safeText(item.locator(".like").first()),
                            safeText(item.locator("[class*='like']").first())
                    ));
                    comments.add(new CommentCandidate(user, sanitizeCommentText(text), likes));
                }
                break;
            } catch (Exception ignored) {
            }
        }

        return comments.stream()
                .filter(candidate -> StringUtils.hasText(candidate.text()))
                .sorted(Comparator.comparing(CommentCandidate::likes).reversed())
                .map(candidate -> formatComment(candidate.user(), candidate.text(), candidate.likes()))
                .distinct()
                .limit(3)
                .toList();
    }

    private String sanitizeCommentText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String formatComment(String user, String text, int likes) {
        String prefix = StringUtils.hasText(user) ? user.trim() + "：" : "";
        String suffix = likes > 0 ? "（赞 " + likes + "）" : "";
        return prefix + text + suffix;
    }

    private String normalizeImageUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("http")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        Matcher matcher = Pattern.compile("url\\(\"?(.*?)\"?\\)").matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return value;
    }

    private void dismissPopups(Page page) {
        List<String> selectors = List.of(".close-btn", ".modal-close", "[class*='close-icon']", "button.close");
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    locator.click();
                    page.waitForTimeout(300);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void scrollPage(Page page, int rounds) {
        for (int i = 0; i < rounds; i++) {
            page.evaluate("window.scrollBy(0, 900)");
            page.waitForTimeout(900);
        }
    }

    private boolean isVideoCard(Locator card) {
        for (String selector : List.of(".video-badge", ".play-icon", ".duration", "[class*='video-mark']")) {
            try {
                if (card.locator(selector).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            String tagText = safeText(card.locator(".bottom-tag-area").first());
            return VIDEO_TYPE_KEYWORDS.stream().anyMatch(tagText::contains);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String safeText(Locator locator) {
        try {
            return locator.count() > 0 ? firstNonBlank(locator.innerText(), "") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String safeAttribute(Locator locator, String attribute) {
        try {
            return locator.count() > 0 ? firstNonBlank(locator.getAttribute(attribute), "") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String safeMetaContent(Page page, String selector) {
        try {
            Locator locator = page.locator(selector).first();
            return locator.count() > 0 ? firstNonBlank(locator.getAttribute("content"), "") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> firstAvailableTags(Page page) {
        for (String selector : List.of("#detail-desc .tag", "#hash-tag", ".tag")) {
            try {
                Locator locator = page.locator(selector);
                if (locator.count() > 0) {
                    List<String> tags = locator.allInnerTexts().stream()
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .toList();
                    if (!tags.isEmpty()) {
                        return tags;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    private String normalizeDate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        Matcher exact = DATE_PATTERN.matcher(trimmed);
        if (exact.find()) {
            return exact.group(1).replace("/", "-");
        }
        Matcher relative = RELATIVE_DAY_PATTERN.matcher(trimmed);
        if (relative.find()) {
            return LocalDate.now().minusDays(Integer.parseInt(relative.group(1))).format(DateTimeFormatter.ISO_DATE);
        }
        if (trimmed.contains("小时前") || trimmed.contains("分钟前") || trimmed.contains("刚刚")) {
            return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        }
        Matcher monthDay = MONTH_DAY_PATTERN.matcher(trimmed);
        if (monthDay.find()) {
            return LocalDate.now().getYear() + "-" + pad(monthDay.group(1)) + "-" + pad(monthDay.group(2));
        }
        return trimmed;
    }

    private Integer parseNumber(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        String low = text.trim().replace(",", "").toLowerCase(Locale.ROOT);
        Matcher matcher = NUMBER_PATTERN.matcher(low);
        if (!matcher.find()) {
            return 0;
        }
        double base = Double.parseDouble(matcher.group(1));
        if (low.contains("w") || low.contains("万")) {
            return (int) (base * 10000);
        }
        if (low.contains("k")) {
            return (int) (base * 1000);
        }
        return (int) base;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeTargetCount(XhsCrawlRequest request) {
        return request.getTargetCount() == null || request.getTargetCount() <= 0 ? 20 : request.getTargetCount();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String pad(String value) {
        return value.length() == 1 ? "0" + value : value;
    }

    private record SearchCard(String url, String author, String cardDate, String keyword, String cardTitle) {
    }

    private record CommentCandidate(String user, String text, int likes) {
    }
}
