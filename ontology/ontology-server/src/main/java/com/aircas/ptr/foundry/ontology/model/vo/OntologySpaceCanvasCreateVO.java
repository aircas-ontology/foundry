package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "画布保存结果")
@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCanvasCreateVO {

    @Schema(name = "spaceId", description = "对象空间id")
    private Integer spaceId;

    @Schema(name = "committed", description = "是否存在结构性冲突：false=整单未写入任何数据，true=已写入可合并的部分")
    private Boolean committed;

    @Schema(name = "saved", description = "本次保存实际写入的数量")
    private CanvasSaveSummary saved;

    @Schema(name = "conflicts", description = "冲突明细；为空表示全部保存成功")
    private List<CanvasConflict> conflicts;

    @Schema(name = "canvas", description = "保存后的最新画布，前端直接替换 base 与当前画布即可")
    private OntologySpaceCanvasCreateParam canvas;

    @Schema(description = "本次保存实际写入的数量")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasSaveSummary {

        @Schema(name = "createdOntologyCount", description = "新建对象数")
        private int createdOntologyCount;

        @Schema(name = "updatedOntologyCount", description = "更新对象数")
        private int updatedOntologyCount;

        @Schema(name = "deletedOntologyCount", description = "删除对象数")
        private int deletedOntologyCount;

        @Schema(name = "createdPropertyCount", description = "新建属性数")
        private int createdPropertyCount;

        @Schema(name = "updatedPropertyCount", description = "更新属性数")
        private int updatedPropertyCount;

        @Schema(name = "deletedPropertyCount", description = "删除属性数")
        private int deletedPropertyCount;

        @Schema(name = "createdLinkCount", description = "新建关系数")
        private int createdLinkCount;

        @Schema(name = "updatedLinkCount", description = "更新关系数")
        private int updatedLinkCount;

        @Schema(name = "deletedLinkCount", description = "删除关系数")
        private int deletedLinkCount;
    }

    @Schema(description = "画布保存冲突")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasConflict {

        @Schema(name = "type", description = "冲突项类型：ONTOLOGY / PROPERTY / LINK")
        private String type;

        @Schema(name = "uniqueIdentifier", description = "冲突项唯一标识；新增项为空")
        private String uniqueIdentifier;

        @Schema(name = "apiName", description = "冲突项api名称")
        private String apiName;

        @Schema(name = "displayName", description = "冲突项显示名称")
        private String displayName;

        @Schema(name = "parentOntologyUniqueIdentifier", description = "属性所属对象的唯一标识，仅 PROPERTY 有值")
        private String parentOntologyUniqueIdentifier;

        @Schema(name = "reason", description = "冲突原因：FIELD_CONFLICT / DELETED_BY_OTHERS / DELETE_CONFLICT / NAME_CONFLICT / "
                + "API_NAME_IMMUTABLE / ENDPOINT_IMMUTABLE / INVALID_BASE / PROPERTY_DELETE_FORBIDDEN / PARENT_DELETED")
        private String reason;

        @Schema(name = "fields", description = "字段级冲突明细，仅 FIELD_CONFLICT 有值")
        private List<FieldConflict> fields;

        @Schema(name = "message", description = "冲突说明")
        private String message;
    }

    @Schema(description = "字段级冲突")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldConflict {

        @Schema(name = "field", description = "字段名")
        private String field;

        @Schema(name = "baseValue", description = "加载时的值")
        private Object baseValue;

        @Schema(name = "currentValue", description = "本次提交的值")
        private Object currentValue;

        @Schema(name = "dbValue", description = "数据库中他人修改后的值")
        private Object dbValue;
    }
}
