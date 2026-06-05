package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceNoteSyncItemResponse {

    private String fileName;

    private String title;

    private String relativePath;

    private String notionPageId;

    private String updatedAt;
}
