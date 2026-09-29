package com.example.demo.infrastructure.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class GraphGatewayPropertiesTest {

    @Test
    void enabledEmailDispatchRequiresOnlyRabbitConfiguration() {
        GraphGatewayProperties properties = new GraphGatewayProperties();
        properties.setEnabled(true);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
