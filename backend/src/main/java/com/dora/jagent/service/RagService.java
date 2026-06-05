package com.dora.jagent.service;

import java.util.List;

public interface RagService {

    float[] embed(String text);

    List<String> similaritySearch(String knowledgeBaseId, String query);
}
