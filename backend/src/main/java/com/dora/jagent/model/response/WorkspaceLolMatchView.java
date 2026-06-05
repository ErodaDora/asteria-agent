package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceLolMatchView {

    private String league;

    private String stage;

    private String status;

    private String timeLabel;

    private String matchup;

    private String scoreLine;

    private String bestOfLabel;

    private String recap;
}
