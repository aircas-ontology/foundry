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
 * <p>黑板键是树级条件用的变量，不属于任何行为节点，所以单独列出；实例由执行请求的 entityPrimaryKeys 给。</p>
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

    public List<String> getBlackboardKeys() {
        return blackboardKeys == null ? new ArrayList<>() : blackboardKeys;
    }
}
