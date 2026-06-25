package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
