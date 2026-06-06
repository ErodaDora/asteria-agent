package com.dora.jagent.model.request;

import com.dora.jagent.model.response.ResearchPaperView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveResearchPaperRequest {

    private ResearchPaperView paper;

    private String collectionName;

    private String note;
}
