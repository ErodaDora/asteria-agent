package com.dora.jagent.service.impl.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeatherSnapshot {

    private String requestedLocation;

    private String resolvedLocation;

    private int weatherCode;

    private String weatherText;

    private double currentTemperature;

    private double apparentTemperature;

    private double minTemperature;

    private double maxTemperature;

    private double currentPrecipitation;

    private int precipitationProbability;

    private double windSpeed;
}
