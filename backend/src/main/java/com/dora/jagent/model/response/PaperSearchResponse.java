package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperSearchResponse {

    private String query;

    private String scope;

    private String venueRaw;

    private String venueCanonical;

    private Integer fromYear;

    private Integer toYear;

    private Integer limit;

    private Integer total;

    private String source;

    private List<ResearchPaperView> papers;
}
