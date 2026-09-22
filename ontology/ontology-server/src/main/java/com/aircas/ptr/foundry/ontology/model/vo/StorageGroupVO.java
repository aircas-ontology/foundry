package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "存储分组VO")
public class StorageGroupVO {

    @Schema(name = "id", description = "主键id", example = "1")
    private Integer id;

    @Schema(name = "ontologyUniqueIdentifier", description = "本体对象唯一标识", example = "ship_001")
    private String ontologyUniqueIdentifier;

    @Schema(name = "storageName", description = "存储名称", example = "postgresql_main")
    private String storageName;

    @Schema(name = "createTime", description = "创建时间")
    private Date createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private Date updateTime;
}
