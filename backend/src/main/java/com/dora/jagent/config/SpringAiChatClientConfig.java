package com.dora.jagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiChatClientConfig {

    // config 目录的意义：
    // 这里不承载具体业务，而是负责告诉 Spring“启动时要把哪些对象装配进容器”。
    // 对多模型场景来说，最典型的装配对象就是不同模型对应的 ChatClient Bean。
    //
    // bean 可以先粗暴理解成：
    // “交给 Spring 托管、可被别处注入复用的对象实例”。
    //
    // 这里约定：一个模型 = 一个 ChatClient Bean。
    @Bean("deepseek-chat")
    public ChatClient deepseekSpringAiChatClient(DeepSeekChatModel deepSeekChatModel) {
        return ChatClient.create(deepSeekChatModel);
    }

    @Bean("glm-4.6")
    public ChatClient zhiPuAiChatClient(ZhiPuAiChatModel zhiPuAiChatModel) {
        return ChatClient.create(zhiPuAiChatModel);
    }

    @Bean("gpt-4o-mini")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.create(openAiChatModel);
    }
}
