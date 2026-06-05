package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.KnowledgeBase;
import com.dora.jagent.model.request.CreateKnowledgeBaseRequest;
import com.dora.jagent.model.response.CreateKnowledgeBaseResponse;
import com.dora.jagent.model.response.KnowledgeBaseView;
import com.dora.jagent.repository.KnowledgeBaseRepository;
import com.dora.jagent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Override
    public List<KnowledgeBaseView> getKnowledgeBases() {
        return knowledgeBaseRepository.findAllOrderByCreatedAtAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public KnowledgeBaseView getKnowledgeBase(String knowledgeBaseId) {
        return toView(getRequiredKnowledgeBase(knowledgeBaseId));
    }

    @Override
    public CreateKnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("knowledge base name cannot be blank");
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .metadata(trimToNull(request.getMetadata()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        knowledgeBaseRepository.save(knowledgeBase);

        return CreateKnowledgeBaseResponse.builder()
                .knowledgeBaseId(knowledgeBase.getId())
                .build();
    }

    private KnowledgeBase getRequiredKnowledgeBase(String knowledgeBaseId) {
        return knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BizException("knowledge base not found"));
    }

    private KnowledgeBaseView toView(KnowledgeBase knowledgeBase) {
        return KnowledgeBaseView.builder()
                .id(knowledgeBase.getId())
                .name(knowledgeBase.getName())
                .description(knowledgeBase.getDescription())
                .metadata(knowledgeBase.getMetadata())
                .createdAt(knowledgeBase.getCreatedAt())
                .updatedAt(knowledgeBase.getUpdatedAt())
                .build();
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }
}
