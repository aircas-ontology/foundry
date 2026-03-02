package com.aircas.ptr.foundry.ontology.model.vo;


import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@ApiModel(value = "函数基本信息VO")
public class FunctionExecuteResultVO {


    private String taskId;

    private FunctionResultVO result;

    private String functionApi;

    private String actionApi;

    private String functionParam;

    private TaskStatusEnum taskStatus;

}
