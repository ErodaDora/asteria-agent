package com.dora.jagent.service.impl.support;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class LolEsportsMatchSnapshot {

    private String eventId;

    private String leagueName;

    private String leagueSlug;

    private String blockName;

    private String state;

    private ZonedDateTime startTime;

    private String team1Name;

    private String team1Code;

    private Integer team1Wins;

    private String team2Name;

    private String team2Code;

    private Integer team2Wins;

    private Integer bestOf;
}
