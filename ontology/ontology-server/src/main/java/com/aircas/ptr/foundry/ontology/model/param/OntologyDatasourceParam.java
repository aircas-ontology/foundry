package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.PrimaryKeyVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.springframework.context.annotation.Primary;

import javax.persistence.PrimaryKeyJoinColumn;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "OntologyDatasourceParam")
public class OntologyDatasourceParam {

    @ApiModelProperty(name ="columnParamList",value = "列参数", required = true)
    @PrimaryKeyVerify
    private List<OntologyDataSourceColumnParam> columnParamList;
}
