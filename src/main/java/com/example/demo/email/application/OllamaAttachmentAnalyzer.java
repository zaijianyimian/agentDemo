package com.example.demo.email.application;

import com.example.demo.infrastructure.properties.EmailAttachmentProperties;
import com.example.demo.model.application.ModelManager;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 通过 Ollama 调用本地 gemma 模型做附件解析。
 *
 * <p>模型选择策略：</p>
 * <ol>
 *   <li>优先在 {@code AiModelConfig} 中取 {@code purpose=attachment} 且 enabled 的模型（OpenAI 兼容）</li>
 *   <li>未配到时使用 application.yaml 的兜底（{@code app.email.default-ollama-base-url} + {@code default-ollama-model}）</li>
 * </ol>
 */
@Slf4j
@Service
public class OllamaAttachmentAnalyzer implements AttachmentAnalyzer {

    /** 解析摘要 prompt 模板：图片。 */
    private static final String IMAGE_PROMPT = """
            你将看到一张图片。请用 2-4 句中文给出简洁摘要，描述：
            1) 图片主题/场景；
            2) 关键文字内容（如果有 OCR 的话）；
            3) 重要数据或事实。
            不要添加任何前言或元说明，直接输出摘要。""";

    /** 解析摘要 prompt 模板：文档。 */
    private static final String TEXT_PROMPT = """
            你将看到一份文档的抽取文本。请用 3-6 句中文给出结构化摘要，涵盖：
            1) 文档主题与类型；
            2) 核心论点或数据；
            3) 行动项、关键日期或人物（如果有）。
            不要逐字复述，只输出摘要。""";

    /** 文档文本最大送入字符数，避免超出模型上下文。 */
    private static final int MAX_TEXT_CHARS = 12_000;

    private final EmailAttachmentProperties properties;
    private final ModelManager modelManager;

    /** 兜底模型懒加载缓存。 */
    private volatile ChatModel fallbackModel;

    public OllamaAttachmentAnalyzer(EmailAttachmentProperties properties, ModelManager modelManager) {
        this.properties = properties;
        this.modelManager = modelManager;
    }

    @Override
    public boolean supports(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.startsWith("image/")
                || lower.equals("application/pdf")
                || lower.startsWith("text/")
                || lower.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || lower.equals("application/msword");
    }

    @Override
    public String analyzeImage(Path imageFile, String contentType) throws Exception {
        if (!Files.exists(imageFile)) {
            throw new IllegalStateException("附件文件不存在: " + imageFile);
        }
        byte[] bytes = Files.readAllBytes(imageFile);
        String base64 = Base64.getEncoder().encodeToString(bytes);

        UserMessage userMessage = UserMessage.from(
                TextContent.from(IMAGE_PROMPT),
                ImageContent.from(base64, contentType == null ? "image/png" : contentType)
        );
        ChatModel chatModel = resolveChatModel();
        log.info("Analyzing image via Ollama: file={}, mime={}, model={}",
                imageFile.getFileName(), contentType, describeModel(chatModel));
        return chatModel.chat(List.of(userMessage)).aiMessage().text();
    }

    @Override
    public String analyzeText(String extractedText, String contentType) throws Exception {
        if (extractedText == null || extractedText.isBlank()) {
            return "（文档抽取后无文本内容）";
        }
        String truncated = extractedText.length() > MAX_TEXT_CHARS
                ? extractedText.substring(0, MAX_TEXT_CHARS) + "\n...（后续内容已截断）"
                : extractedText;

        UserMessage userMessage = UserMessage.from(
                TextContent.from(TEXT_PROMPT + "\n\n---\n\n" + truncated)
        );
        ChatModel chatModel = resolveChatModel();
        log.info("Analyzing text via Ollama: chars={}, mime={}, model={}",
                truncated.length(), contentType, describeModel(chatModel));
        return chatModel.chat(List.of(userMessage)).aiMessage().text();
    }

    /**
     * 优先 AiModelConfig(purpose=attachment)，未配到时使用 application.yaml 的兜底。
     */
    private ChatModel resolveChatModel() {
        ChatModel configured = modelManager.getChatModelByPurpose(properties.getAttachmentPurpose());
        if (configured != null) {
            return configured;
        }
        ChatModel local = fallbackModel;
        if (local == null) {
            synchronized (this) {
                local = fallbackModel;
                if (local == null) {
                    local = OllamaChatModel.builder()
                            .baseUrl(properties.getDefaultOllamaBaseUrl())
                            .modelName(properties.getDefaultOllamaModel())
                            .timeout(Duration.ofMinutes(3))
                            .build();
                    fallbackModel = local;
                    log.info("Initialized fallback Ollama chat model: baseUrl={}, model={}",
                            properties.getDefaultOllamaBaseUrl(), properties.getDefaultOllamaModel());
                }
            }
        }
        return local;
    }

    private String describeModel(ChatModel chatModel) {
        return chatModel.getClass().getSimpleName();
    }
}
