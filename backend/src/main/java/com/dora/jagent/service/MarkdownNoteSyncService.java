package com.dora.jagent.service;

import com.dora.jagent.model.response.WorkspaceNoteSyncResponse;

public interface MarkdownNoteSyncService {

    WorkspaceNoteSyncResponse scanNotes();

    WorkspaceNoteSyncResponse syncNotes();
}
