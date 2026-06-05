package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.xhs.service.XhsImageGenerationService;
import org.springframework.stereotype.Service;

@Service
public class XhsImageGenerationServiceImpl implements XhsImageGenerationService {

    @Override
    public String resolveImageGenerationStatus(String modelKey, boolean generateImages) {
        if (!generateImages) {
            return "本次未开启自动配图。";
        }
        if ("deepseek-chat".equals(modelKey)) {
            return "当前模型为 deepseek-chat，已按规则跳过自动生图，仅保留配图建议文本。";
        }
        return "当前项目暂未接入自动生图执行，仅保留配图建议文本。";
    }
}
