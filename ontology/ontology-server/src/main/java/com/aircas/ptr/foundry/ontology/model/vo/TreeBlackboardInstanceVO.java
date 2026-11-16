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
 * 行为树黑板里来源是对象属性的键：执行前要按对象选实例，这些键才读得到库里的当前值。
 * <p>黑板键是树级条件用的变量，不属于任何行为节点，所以单独列出；实例由执行请求的 entityPrimaryKeys 给。
 * 属性不在主属性分组时，一个实例下有多行，还要按 storageGroups 再选该分组的一行，按 inputDataPrimaryKeys 传。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树黑板属性键的实例要求")
public class TreeBlackboardInstanceVO {

    @Schema(name = "objectIdentifier", description = "要在哪个对象类型下选实例", example = "satellite")
    private String objectIdentifier;

    @Schema(name = "blackboardKeys", description = "该对象实例是给这些黑板键读属性用的", example = "[\"speed\"]")
    private List<String> blackboardKeys = new ArrayList<>();

    @Schema(name = "storageGroups",
            description = "这些黑板键里属性不在主属性分组的部分，按分组列出；每个分组要再选一行数据，"
                    + "在 inputDataPrimaryKeys[对象标识][分组] 里传行主键。为空表示属性都在主分组，取实例本身的行")
    private List<TreeBlackboardGroupVO> storageGroups = new ArrayList<>();

    public List<String> getBlackboardKeys() {
        return blackboardKeys == null ? new ArrayList<>() : blackboardKeys;
    }

    public List<TreeBlackboardGroupVO> getStorageGroups() {
        return storageGroups == null ? new ArrayList<>() : storageGroups;
    }
}
