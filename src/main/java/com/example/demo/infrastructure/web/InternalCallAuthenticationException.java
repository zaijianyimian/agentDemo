package com.example.demo.infrastructure.web;

import lombok.Getter;

/**
 * 内部调用认证失败。
 *
 * <p>放在 {@code infrastructure} 模块内而不是 {@code shared::web}：模块边界只允许
 * infrastructure 依赖 {@code shared::context}、{@code shared::dto} 与 {@code system::*}，
 * 认证器不能反向依赖 web 层。HTTP 层的错误码翻译由调用方（如 schedule 模块的
 * {@code InternalScheduleController}）完成。</p>
 */
@Getter
public class InternalCallAuthenticationException extends RuntimeException {

    /** 稳定失败原因码，用于日志与上游判定。 */
    private final String reasonCode;

    public InternalCallAuthenticationException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }
}
