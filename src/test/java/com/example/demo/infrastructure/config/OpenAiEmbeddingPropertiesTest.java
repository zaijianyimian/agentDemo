package com.example.demo.infrastructure.config;

import com.example.demo.infrastructure.properties.OpenAiEmbeddingProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiEmbeddingPropertiesTest {

    @Test
    void defaultsStayDisabled() {
        OpenAiEmbeddingProperties properties = new OpenAiEmbeddingProperties();
        assertEquals(false, properties.isEnabled());
        assertEquals("https://api.openai.com/v1", properties.getBaseUrl());
        assertEquals("text-embedding-3-small", properties.getModelName());
        assertEquals(0, properties.getDimensions());
        assertTrue(properties.getApiKey() == null || properties.getApiKey().isBlank());
    }
}
