package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为测试的输入展开方式：多个候选值（各对象实例、各入参数据位置）按这里选的方式组合成多组逐组执行。
 * <p>只属于测试请求，不落库：行为保存的绑定形状与它无关。</p>
 */
@Getter
@AllArgsConstructor
public enum ActionTestInputModeEnum {

    /** 各取值轴的候选值自由组合 */
    CARTESIAN("笛卡尔积"),
    /** 各取值轴按下标配对成组，候选值个数必须一致 */
    GROUP("按下标配对"),
    /** 不选实例和行：遍历行为涉及对象下的全部实例，涉及多个对象时做笛卡尔积 */
    OBJECT("遍历全部实例");

    private final String name;
}