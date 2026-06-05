package com.dora.jagent.service;

import com.dora.jagent.model.response.XhsNoteItemView;

import java.util.List;

public interface XhsTextProcessingService {

    List<XhsNoteItemView> normalize(List<XhsNoteItemView> items);
}
