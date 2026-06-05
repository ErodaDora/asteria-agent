package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkspaceLolEsportsResponse {

    private String title;

    private String subtitle;

    private String dateLabel;

    private List<WorkspaceLolMatchView> matches;
}
