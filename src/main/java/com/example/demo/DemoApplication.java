package com.example.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动入口。
 * <p>
 * 基于 Spring Boot 与 Spring Modulith 构建,启用模块化架构、定时任务、
 * 配置属性扫描以及 MyBatis Mapper 扫描,启动后初始化整个后端应用上下文。
 */
@Modulithic(
        additionalPackages = "com.example.demo"
)
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("com.example.demo.*.persistence")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
