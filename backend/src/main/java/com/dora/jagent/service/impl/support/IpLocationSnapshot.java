package com.dora.jagent.service.impl.support;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IpLocationSnapshot {

    private String requestedIp;

    private String resolvedIp;

    private String country;

    private String region;

    private String city;

    private String district;

    private String displayName;

    private double latitude;

    private double longitude;
}
