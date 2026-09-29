package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行为函数版本升级预览/执行结果")
public class ActionFunctionVersionUpgradeVO {

    private String actionApi;
    private Long sourceFunctionVersionId;
    private Integer sourceFunctionVersionNo;
    private Long targetFunctionVersionId;
    private Integer targetFunctionVersionNo;
    private boolean canUpgrade;
    private boolean upgraded;
    private List<ParamMatch> parameterMatches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamMatch {
        @Schema(description = "AUTO_MATCHED/PARAMETER_ADDED/PARAMETER_DELETED/TYPE_CHANGED/AMBIGUOUS")
        private String status;
        private String paramName;
        private FunctionParamCategoryEnum category;
        private Long sourceParamId;
        private FunctionParamTypeEnum sourceParamType;
        private Long targetParamId;
        private FunctionParamTypeEnum targetParamType;
        private boolean mappedByAction;
        private String message;
    }
}
