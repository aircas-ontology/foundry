package com.aircas.ptr.foundry.ontology.script.security;

import com.aircas.ptr.foundry.ontology.config.ScriptSecurityProperties;
import com.aircas.ptr.foundry.ontology.script.security.model.ScriptAstInventory;
import lombok.Data;

/**
 * 规则执行上下文：规则只读上下文与清单，不直接触碰 AST。
 */
@Data
public class ScriptSecurityContext {

    /** 原始脚本（规则仅用于生成片段，禁止整段写日志） */
    private final String code;

    private final ScriptAstInventory inventory;

    private final ScriptSecurityProperties properties;

    public ScriptSecurityContext(String code, ScriptAstInventory inventory, ScriptSecurityProperties properties) {
        this.code = code;
        this.inventory = inventory;
        this.properties = properties;
    }
}
