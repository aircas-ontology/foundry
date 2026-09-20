package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体关系更新请求")
public class OntologyLinkUpdateParam extends IdentifierParam {

    @Schema(name = "name", required = false, description = "关系名称")
    private String name;

    @Schema(name = "apiName", required = false, description = "关系在代码中使用的api名称")
    private String apiName;

    @Schema(name = "comment", required = false, description = "关系备注/描述，可置空")
    private String comment;
}
