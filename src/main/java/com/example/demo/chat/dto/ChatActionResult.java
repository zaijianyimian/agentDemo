package com.example.demo.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 表示聊天内容转化为业务对象后的执行结果。
 * <p>
 * 携带目标类型、提示信息、实体标识及页面跳转数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatActionResult {

    private String target;

    private String message;

    private Long entityId;

    private String route;

    private Map<String, Object> payload;
}
