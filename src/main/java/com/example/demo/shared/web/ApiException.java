package com.example.demo.shared.web;

import lombok.Getter;

/**
 * 携带稳定业务错误码的受控异常。
 *
 * <p>用于需要精确控制 HTTP 状态与对外错误码的场景（内部调用鉴权、幂等冲突等）。
 * message 是面向调用方的稳定说明，详细原因写日志，不回显内部细节。</p>
 */
@Getter
public class ApiException extends RuntimeException {

    private final String code;
    private final int status;

    public ApiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
