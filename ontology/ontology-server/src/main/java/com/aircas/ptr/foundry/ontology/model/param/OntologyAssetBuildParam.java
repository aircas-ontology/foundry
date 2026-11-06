package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 资产构建对象入参：对象字段与 {@link OntologyMetaCreateParam}（正常保存对象）完全一致，
 * 额外带上该对象下的属性列表，服务端先建对象再逐条建属性。
 *
 * <p>properties 直接复用属性新增接口的 {@link OntologyPropertyCreateParam}，字段与校验口径
 * 同 {@code POST /property}（datasource 列绑定、storageGroup、tag、defaultValue、metadata 均可传）。
 * 其中 {@code ontologyIdentifier} 无需传，服务端一律用本次新建对象的 uniqueIdentifier 覆盖。</p>
 *
 * <p>属性可带 metadata，服务端会从所有属性的 metadata 自动推导并创建元数据 schema（单根树，
 * 枚举值不推断），然后再创建属性。落库时序固定为「建对象 → 建元数据 schema → 建属性」。</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "资产构建对象请求：保存对象并连带创建其属性")
public class OntologyAssetBuildParam extends OntologyMetaCreateParam {

    @Schema(name = "properties",
            description = "对象属性列表，字段同属性新增接口；ontologyIdentifier 无需传，由服务端用新建对象的 uniqueIdentifier 覆盖",
            example = "[{\"displayName\":\"舰名\",\"apiName\":\"ship_name\",\"dataType\":\"String\",\"storageGroup\":\"main\",\"isPrimaryKey\":true,\"isTitleKey\":false}]")
    @Valid
    private List<OntologyPropertyCreateParam> properties;

}
