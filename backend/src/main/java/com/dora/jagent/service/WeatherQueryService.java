package com.dora.jagent.service;

import com.dora.jagent.service.impl.support.WeatherSnapshot;

public interface WeatherQueryService {

    WeatherSnapshot getTodayWeather(String location);

    WeatherSnapshot getTodayWeather(String resolvedLocation, double latitude, double longitude);
}
