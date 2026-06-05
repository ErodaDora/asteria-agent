package com.dora.jagent.xhs.service;

import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.xhs.model.XhsAnalysisResult;

import java.util.List;

public interface XhsAnalysisService {

    XhsAnalysisResult analyze(List<XhsNoteItemView> items);
}
