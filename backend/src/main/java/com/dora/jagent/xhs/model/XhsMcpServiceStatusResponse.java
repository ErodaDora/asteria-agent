package com.dora.jagent.xhs.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class XhsMcpServiceStatusResponse {

    private boolean running;

    private int port;

    private String binary;

    private String message;
}
