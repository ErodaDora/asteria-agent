package com.dora.jagent.watermark.model;

import lombok.Data;

import java.util.List;

@Data
public class WatermarkEmbedRequest {

    private List<String> inputPaths;

    private String inputDir;

    private String outputDir;

    private Integer limit;
}
