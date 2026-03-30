package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxItem {

    private String category;

    private String title;

    private String summary;

    private String status;

    private String route;

    private String accent;

    private LocalDateTime time;

    private Map<String, Object> meta;
}
