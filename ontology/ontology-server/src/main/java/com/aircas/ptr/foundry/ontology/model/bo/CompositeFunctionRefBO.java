package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 被函数编排引用的原子函数快照，校验器据此判断引用是否存在、是否已发布以及端口是否匹配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositeFunctionRefBO {

    /** 函数 api（唯一编码），与编排节点的 functionId 对应 */
    private String functionApi;

    private String displayName;

    /** 是否已发布（对应 function.status = ENABLE） */
    private boolean published;

    /** 编排节点引用的版本，当前服务端以函数 api 唯一定位，版本仅用于回显 */
    private String version;

    private List<CompositeParameter> inputs;

    private List<CompositeParameter> outputs;

    public List<CompositeParameter> getInputs() {
        return inputs == null ? new ArrayList<>() : inputs;
    }

    public List<CompositeParameter> getOutputs() {
        return outputs == null ? new ArrayList<>() : outputs;
    }
}
