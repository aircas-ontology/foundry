package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 实例 URL 保存入参：只声明用户可写的四个字段，主键 id 与存入时间由后台生成，不接受请求传入。
 * <p>{@code objectId} 传对象的 unique_identifier（即其余接口里的 ontologyIdentifier），
 * {@code instanceId} 传实例主键值，数字主键按字符串传即可。</p>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实例 URL 保存入参")
public class OntologyInstanceUrlSaveParam {

    @Schema(name = "spaceId", description = "对象空间 id", example = "1", required = true)
    @NotNull(message = "spaceId is empty")
    @SpaceIdVerify
    private Integer spaceId;

    @Schema(name = "objectId", description = "对象 id（ontology_meta.unique_identifier）",
            example = "abcdef", required = true)
    @NotBlank(message = "对象 id 不能为空")
    @Size(max = 255, message = "对象 id 最长 255 字")
    @OntologyIdVerify
    private String objectId;

    @Schema(name = "instanceId", description = "实例 id（实例主键值，数字主键按字符串传）",
            example = "123", required = true)
    @NotBlank(message = "实例 id 不能为空")
    @Size(max = 255, message = "实例 id 最长 255 字")
    private String instanceId;

    @Schema(name = "url", description = "实例 id 对应的 URL",
            example = "https://172.16.18.58/detail/123", required = true)
    @NotBlank(message = "URL 不能为空")
    @Size(max = 1024, message = "URL 最长 1024 字")
    private String url;
}
