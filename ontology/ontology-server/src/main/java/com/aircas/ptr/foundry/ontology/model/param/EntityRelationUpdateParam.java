package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "实体关系更新请求")
public class EntityRelationUpdateParam {

    @Schema(name = "linkUniqIdentifier" , description = "关系id", example = "edf3451")
    @NotBlank(message = "linkUniqIdentifier is empty")
    private String linkUniqIdentifier;

    @Schema(name = "entityPrimaryKeyFrom", description = "开始实体主键值")
    @NotNull(message = "entityPrimaryKeyFrom is null")
    private Object entityPrimaryKeyFrom;

    @Schema(name = "entityPrimaryKeyTo", description = "结束实体主键值")
    @NotNull(message = "entityPrimaryKeyFrom is null")
    private Object entityPrimaryKeyTo;


    @Schema(name = "visibilityWindows", description = "可见窗口列表")
    private List<VisibilityWindow> visibilityWindows;


    @Schema(name = "status", description = "关系状态")
    @NotNull(message = "status is null")
    private Status status;
}
