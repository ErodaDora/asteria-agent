package com.dora.jagent.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.io.InputStream;
import java.util.List;

public interface MarkdownParserService {

    List<MarkdownSection> parseMarkdown(InputStream inputStream);

    @Data
    @AllArgsConstructor
    @ToString
    class MarkdownSection {
        private String title;
        private String content;
    }
}
