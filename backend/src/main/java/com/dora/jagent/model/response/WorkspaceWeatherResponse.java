package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceWeatherResponse {

    private String location;

    private String headline;

    private String trend;

    private String rainAdvice;

    private String dressAdvice;

    private String detail;
}
