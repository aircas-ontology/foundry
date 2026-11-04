package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "行为关系新增")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionLinkMappingParam {

    @Schema(description = "行为关联的关系id")
    private String ontologyLinkUniqIdentifier;

    @Schema(description = "行为关联的函数参数表达式（仅存储，执行时不再参与关系判定；关系是否成立只看算子返回的可见窗口）",
            deprecated = true)
    private String ontologyLinkFunctionParamExpression;

    @Schema(description = "关系-修改操作的目标状态（ENABLE/DISABLE），在行为定义里固定；其余操作不传", example = "DISABLE")
    private Status targetStatus;

    @Schema(description = "实例关系的持续时段（行为定义里固定，执行时原样写到图库的边上）；" +
            "新增必填，修改填了才改、不填保留边上原值，删除不传")
    private List<VisibilityWindow> visibilityWindows;

}
