package com.aircas.ptr.foundry.runner.lang;

/**
 * 脚本编译/校验失败异常，携带可读错误信息，由 Controller 转为 {ok:false, error:...}。
 */
public class ScriptCompileException extends RuntimeException {

    public ScriptCompileException(String message) {
        super(message);
    }

    public ScriptCompileException(String message, Throwable cause) {
        super(message, cause);
    }

}
