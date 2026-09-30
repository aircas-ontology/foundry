package com.aircas.ptr.foundry.ontology.script.security.model;

/**
 * 脚本安全违规严重程度。
 * <p>
 * DANGER：一律拦截（受 block-on-violation 控制）；WARN：默认记录，受 block-on-warn 控制是否升级为拦截。
 */
public enum SecuritySeverity {

    DANGER,

    WARN
}
