package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑板里对象属性来源的键按存储分组归类：属性不在主属性分组时，实例本身定不到值，
 * 还要再选该分组的一行数据，条件求值前从这一行回读。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树黑板属性键的分组行要求")
public class TreeBlackboardGroupVO {

    @Schema(name = "storageGroup", description = "存储分组名称，与行详情接口 propertyGroups[].storageGroup 一致", example = "weapon_load")
    private String storageGroup;

    @Schema(name = "blackboardKeys", description = "绑定在该分组的黑板键，执行时都要从同一行回读", example = "[\"ammoCount\"]")
    private List<String> blackboardKeys = new ArrayList<>();

    public List<String> getBlackboardKeys() {
        return blackboardKeys == null ? new ArrayList<>() : blackboardKeys;
    }
}
