package com.dora.jagent.service.impl.support;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HupuLolPostSnapshot {

    private String title;

    private String sourceUrl;

    private String articleBody;

    private List<String> topComments;
}
