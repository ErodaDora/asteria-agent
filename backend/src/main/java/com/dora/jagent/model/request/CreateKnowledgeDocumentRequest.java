package com.dora.jagent.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeDocumentRequest {

    private String filename;

    private String filetype;

    private Long size;

    // 当前先直接接 JSON 字符串，便于后面存文件路径等信息。
    private String metadata;
}
