package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkspaceNoteSyncResponse {

    private String rootPath;

    private boolean notionConfigured;

    private int noteCount;

    private int boundCount;

    private String latestUpdatedAt;

    private String statusText;

    private List<WorkspaceNoteSyncItemResponse> items;
}
