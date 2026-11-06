package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorCompareOpEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 黑板条件节点的配置：取黑板键的值按比较方式与 value 比。
 * <p>key 必须是已声明的黑板键名；IS_EMPTY/IS_NOT_EMPTY 不取 value，其余运算符必须给 value。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树黑板条件节点配置")
public class BehaviorBlackboardConditionConfig {

    @Schema(name = "key", description = "黑板键名", example = "ammoCount")
    private String key;

    @Schema(name = "op", description = "比较方式", example = "GT")
    private BehaviorCompareOpEnum op;

    @Schema(name = "value", description = "比较值，按黑板键声明的类型做转换与校验")
    private Object value;
}
