package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class XhsNoteItemView {

    private String title;

    private String content;

    private Integer likes;

    private Integer favorites;

    private Integer comments;

    private List<String> tags;

    private String author;

    private String publishTime;

    private String url;

    private String coverImageUrl;

    private List<String> hotComments;

    private String contentType;

    private String keywordUsed;
}
