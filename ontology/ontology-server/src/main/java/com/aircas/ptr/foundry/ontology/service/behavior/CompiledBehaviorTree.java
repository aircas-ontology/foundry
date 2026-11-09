package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.ontology.model.param.BehaviorTreeDefinition;
import com.github.silencesu.behavior3java.core.BehaviorTree;

import java.util.Date;

/**
 * 一棵已编译的行为树：behavior3java 的可执行实例 + 还原结果树用的原始定义。
 * <p>树没有版本，行为定义一改就重新编译；编译结果按「空间 + api」缓存，保存/发布/删除时失效。
 * updateTime 用于命中缓存时回库核对，防止其他实例改了定义而本地还在跑旧树。</p>
 */
public record CompiledBehaviorTree(String api, Integer spaceId, Integer status, Date updateTime,
                                   BehaviorTreeDefinition definition, BehaviorTree tree) {
}
