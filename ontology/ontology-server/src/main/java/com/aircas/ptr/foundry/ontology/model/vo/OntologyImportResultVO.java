package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对象批量导入结果")
public class OntologyImportResultVO {

    @Schema(description = "本次成功导入的对象数")
    private int importedOntologyCount;

    @Schema(description = "本次成功导入的关系数")
    private int importedRelationCount;

    @Schema(description = "本次因端点对象不存在被跳过的关系数")
    private int skippedRelationCount;

    @Schema(description = "导入失败的对象 displayName")
    private List<String> failedOntologies;

    @Schema(description = "本次因目标空间重名而被自动改名的条目，未发生改名时为空")
    private List<RenamedItem> renamed;

    @Data
    @Builder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "重名自动改名条目")
    public static class RenamedItem {

        @Schema(description = "改名对象类型：ONTOLOGY=对象, RELATION=关系")
        private String kind;

        @Schema(description = "改名前的显示名称（关系为关系名称）")
        private String displayNameFrom;

        @Schema(description = "改名后的显示名称（关系为关系名称）")
        private String displayNameTo;

        @Schema(description = "改名前的 apiName，关系未提供 apiName 时为空")
        private String apiNameFrom;

        @Schema(description = "改名后的 apiName，关系未提供 apiName 时为空")
        private String apiNameTo;
    }
}
