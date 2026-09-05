package com.example.demo;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动入口的无外部依赖冒烟测试。
 *
 * <p>不要在默认单元测试套件中启动完整 Spring 上下文：应用启动会连接用户配置的
 * MySQL、邮箱和模型服务。模块装配及边界由 {@link ModulithStructureTest} 独立验证。</p>
 */
class DemoApplicationTests {

    @Test
    void applicationEntryPointDeclaresRequiredInfrastructure() {
        assertThat(DemoApplication.class)
                .hasAnnotation(SpringBootApplication.class)
                .hasAnnotation(Modulithic.class)
                .hasAnnotation(EnableScheduling.class)
                .hasAnnotation(MapperScan.class);
    }

}
