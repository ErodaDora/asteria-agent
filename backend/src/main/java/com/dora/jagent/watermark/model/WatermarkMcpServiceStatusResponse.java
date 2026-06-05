package com.dora.jagent.watermark.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WatermarkMcpServiceStatusResponse {

    private boolean running;

    private int port;

    private String binary;

    private String script;

    private String message;
}
