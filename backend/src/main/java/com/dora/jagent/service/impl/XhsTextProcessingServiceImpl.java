package com.dora.jagent.service.impl;

import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.service.XhsTextProcessingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class XhsTextProcessingServiceImpl implements XhsTextProcessingService {

    @Override
    public List<XhsNoteItemView> normalize(List<XhsNoteItemView> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(this::normalizeItem)
                .toList();
    }

    private XhsNoteItemView normalizeItem(XhsNoteItemView item) {
        return XhsNoteItemView.builder()
                .title(clean(item.getTitle()))
                .content(clean(item.getContent()))
                .likes(item.getLikes())
                .favorites(item.getFavorites())
                .comments(item.getComments())
                .tags(item.getTags() == null ? List.of() : item.getTags().stream().map(this::clean).filter(StringUtils::hasText).distinct().toList())
                .author(clean(item.getAuthor()))
                .publishTime(clean(item.getPublishTime()))
                .url(clean(item.getUrl()))
                .coverImageUrl(clean(item.getCoverImageUrl()))
                .hotComments(item.getHotComments() == null ? List.of() : item.getHotComments().stream().map(this::clean).filter(StringUtils::hasText).distinct().toList())
                .contentType(clean(item.getContentType()))
                .keywordUsed(clean(item.getKeywordUsed()))
                .build();
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
