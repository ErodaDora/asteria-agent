package com.dora.jagent.service;

import com.dora.jagent.model.response.WorkspaceWeatherResponse;
import com.dora.jagent.model.response.WorkspaceLolEsportsResponse;
import com.dora.jagent.model.response.WorkspaceNoteSyncResponse;

public interface WorkspaceService {

    WorkspaceWeatherResponse getTodayWeatherCard(String clientIp);

    WorkspaceLolEsportsResponse getTodayLolEsportsCard();

    WorkspaceNoteSyncResponse getNoteSyncCard();

    WorkspaceNoteSyncResponse syncNotesToNotion();
}
