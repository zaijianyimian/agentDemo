package com.example.demo.service.chatimport;

import com.example.demo.entity.VirtualAssistant;
import com.example.demo.mapper.VirtualAssistantMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 虚拟助手服务
 * 基于导入的聊天记录进行智能对话
 */
@Slf4j
@Service
public class VirtualAssistantService {

    private final VirtualAssistantMapper virtualAssistantMapper;
    private final ChatHistoryVectorService chatHistoryVectorService;
    private final ChatModel chatModel;

    public VirtualAssistantService(VirtualAssistantMapper virtualAssistantMapper,
                                   ChatHistoryVectorService chatHistoryVectorService,
                                   ChatModel chatModel) {
        this.virtualAssistantMapper = virtualAssistantMapper;
        this.chatHistoryVectorService = chatHistoryVectorService;
        this.chatModel = chatModel;
    }

    /**
     * 获取所有启用的助手
     */
    public List<VirtualAssistant> getAllAssistants() {
        return virtualAssistantMapper.findAllEnabled();
    }

    /**
     * 获取助手详情
     */
    public VirtualAssistant getAssistant(Long id) {
        return virtualAssistantMapper.selectById(id);
    }

    /**
     * 更新助手
     */
    public VirtualAssistant updateAssistant(Long id, String name, String description, Boolean enabled) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(id);
        if (assistant == null) {
            return null;
        }
        if (name != null && !name.isBlank()) {
            assistant.setName(name);
        }
        if (description != null) {
            assistant.setDescription(description);
        }
        if (enabled != null) {
            assistant.setEnabled(enabled);
        }
        assistant.setUpdateTime(LocalDateTime.now());
        virtualAssistantMapper.updateById(assistant);
        return assistant;
    }

    /**
     * 删除助手
     */
    public boolean deleteAssistant(Long id) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(id);
        if (assistant == null) {
            return false;
        }
        // 删除向量数据
        chatHistoryVectorService.deleteAssistantVectors(id);
        // 删除助手记录
        virtualAssistantMapper.deleteById(id);
        return true;
    }

    /**
     * 与助手对话
     * @param assistantId 助手ID
     * @param userMessage 用户消息
     * @param history 历史对话上下文
     * @return 助手回复
     */
    public String chat(Long assistantId, String userMessage, List<Map<String, String>> history) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(assistantId);
        if (assistant == null || !Boolean.TRUE.equals(assistant.getEnabled())) {
            return "助手不存在或未启用";
        }

        // 搜索相似的聊天记录作为参考
        List<Map<String, Object>> similarMessages = chatHistoryVectorService.searchSimilarMessages(assistantId, userMessage, 5);

        // 构建系统提示
        String systemPrompt = buildSystemPrompt(assistant, similarMessages);

        // 构建对话历史
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        // 添加历史对话
        if (history != null && !history.isEmpty()) {
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    messages.add(UserMessage.from(content));
                } else if ("assistant".equals(role)) {
                    messages.add(AiMessage.from(content));
                }
            }
        }

        // 添加当前用户消息
        messages.add(UserMessage.from(userMessage));

        try {
            // 调用AI模型
            var response = chatModel.chat(messages);
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("虚拟助手对话失败: assistantId={}", assistantId, e);
            return "对话处理出错: " + e.getMessage();
        }
    }

    /**
     * 构建系统提示
     */
    private String buildSystemPrompt(VirtualAssistant assistant, List<Map<String, Object>> similarMessages) {
        StringBuilder sb = new StringBuilder();

        // 基础身份
        sb.append("你是").append(assistant.getName()).append("。");

        if (assistant.getDescription() != null && !assistant.getDescription().isBlank()) {
            sb.append("\n角色描述：").append(assistant.getDescription());
        }

        if (assistant.getPersonalitySummary() != null && !assistant.getPersonalitySummary().isBlank()) {
            sb.append("\n人格特征：").append(assistant.getPersonalitySummary());
        }

        if (assistant.getTopics() != null && !assistant.getTopics().isBlank()) {
            sb.append("\n擅长话题：").append(assistant.getTopics());
        }

        // 添加参考对话示例
        if (!similarMessages.isEmpty()) {
            sb.append("\n\n以下是参考对话示例，请学习其中的说话风格：\n");
            for (Map<String, Object> msg : similarMessages) {
                String sender = (String) msg.get("sender");
                String senderType = (String) msg.get("senderType");
                String content = (String) msg.get("content");
                double score = getScoreSafely(msg);

                if (score >= 0.5 && content != null && !content.isBlank()) {
                    sb.append(sender).append("(").append(senderType).append("): ");
                    sb.append(truncate(content, 200)).append("\n");
                }
            }
        }

        sb.append("\n请保持一致的说话风格和人格特征进行回复。");

        return sb.toString();
    }

    /**
     * 安全获取score值
     */
    private double getScoreSafely(Map<String, Object> msg) {
        Object scoreObj = msg.get("score");
        if (scoreObj == null) {
            return 0.0;
        }
        if (scoreObj instanceof Number) {
            return ((Number) scoreObj).doubleValue();
        }
        try {
            return Double.parseDouble(scoreObj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 分析助手人格特征（基于聊天记录）
     */
    public void analyzePersonality(Long assistantId) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(assistantId);
        if (assistant == null) {
            return;
        }

        // 获取用户的聊天记录样本
        List<Map<String, Object>> samples = chatHistoryVectorService.searchSimilarMessages(assistantId, "用户说了什么", 20);

        if (samples.isEmpty()) {
            return;
        }

        // 构建分析请求
        StringBuilder sampleText = new StringBuilder();
        for (Map<String, Object> msg : samples) {
            String sender = (String) msg.get("sender");
            String content = (String) msg.get("content");
            if ("user".equals(msg.get("senderType")) && content != null && !content.isBlank()) {
                sampleText.append(sender).append(": ").append(content).append("\n");
            }
        }

        String analysisPrompt = "请分析以下聊天记录中用户的说话风格、人格特征和常用话题，并用简洁的中文总结：\n\n" + sampleText;

        try {
            List<ChatMessage> messages = List.of(UserMessage.from(analysisPrompt));
            var response = chatModel.chat(messages);
            String analysis = response.aiMessage().text();

            // 更新助手的personalitySummary
            assistant.setPersonalitySummary(truncate(analysis, 500));
            assistant.setUpdateTime(LocalDateTime.now());
            virtualAssistantMapper.updateById(assistant);

            // 分析话题
            analyzeTopics(assistantId, sampleText.toString());
        } catch (Exception e) {
            log.error("分析人格特征失败: assistantId={}", assistantId, e);
        }
    }

    /**
     * 分析常用话题
     */
    private void analyzeTopics(Long assistantId, String sampleText) {
        String topicPrompt = "请从以下聊天记录中提取主要话题关键词（用逗号分隔，最多5个）：\n\n" + sampleText;

        try {
            List<ChatMessage> messages = List.of(UserMessage.from(topicPrompt));
            var response = chatModel.chat(messages);
            String topics = response.aiMessage().text();

            VirtualAssistant assistant = virtualAssistantMapper.selectById(assistantId);
            if (assistant != null) {
                assistant.setTopics(truncate(topics, 100));
                assistant.setUpdateTime(LocalDateTime.now());
                virtualAssistantMapper.updateById(assistant);
            }
        } catch (Exception e) {
            log.warn("分析话题失败: assistantId={}", assistantId, e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}