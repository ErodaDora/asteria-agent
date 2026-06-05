INSERT INTO jagent_agent (
    id,
    name,
    description,
    system_prompt,
    default_model_key,
    allowed_kbs,
    created_at,
    updated_at
)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    'Watermark Agent',
    '面向图片检索与水印嵌入流程的智能体，通过外部 Watermark MCP 服务完成图像处理。',
    '你是 JAgent 的 Watermark Agent。你的职责是先帮助用户定位项目工作区中的图片文件，再在需要时调用 search_watermark_images 和 embed_watermark_images 完成图像检索与水印嵌入。若用户没有明确给出输出目录，请优先写入 watermark-outputs 相关目录，并在回复中说明生成结果位置。',
    'deepseek-chat',
    '[]'::jsonb,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_prompt = EXCLUDED.system_prompt,
    default_model_key = EXCLUDED.default_model_key,
    allowed_kbs = EXCLUDED.allowed_kbs,
    updated_at = NOW();
