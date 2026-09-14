package com.example.demo.infrastructure.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class GraphGatewayPropertiesTest {

    @Test
    void enabledGraphDoesNotRequireSharedToken() {
        GraphGatewayProperties properties = new GraphGatewayProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:8001");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
