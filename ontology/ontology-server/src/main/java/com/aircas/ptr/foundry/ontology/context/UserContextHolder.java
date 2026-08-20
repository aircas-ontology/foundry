package com.aircas.ptr.foundry.ontology.context;

import com.aircas.ptr.foundry.ontology.model.dto.UserContextDTO;

/**
 * 用户上下文 ThreadLocal 持有者，供请求链路中各服务获取当前登录用户信息。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContextDTO> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContextDTO userContext) {
        CONTEXT.set(userContext);
    }

    public static UserContextDTO get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
