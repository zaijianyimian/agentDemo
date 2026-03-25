package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

    private String host = "http://localhost";
    private int port = 6334;

}
