package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import lombok.Getter;
import lombok.Setter;

/**
 * 单个节点的执行痕迹，执行结束后据此还原节点结果树。
 * <p>状态由包在节点外面的 Guard 记录，说明由节点自己写；两者分开，Guard 记录状态时不会冲掉说明。</p>
 */
@Getter
@Setter
public class BehaviorNodeTrace {

    private BehaviorStateEnum state;

    private boolean skipped;

    private String message;
}
