package com.aircas.ptr.api.common.params;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
@ApiModel("ptr任务执行请求信息")
public class PtrParams {

    @ApiModelProperty(value = "要执行的ptr接口名称或者路径", required = true, example = "shipLocation")
    private String api;

    @ApiModelProperty(value = "请求的数据内容", required = true, example = "{'mb':'123','target':'xx','type':'qzj'}")
    private Map<String,Object> body;

    @ApiModelProperty(value = "请求机构或组织/个人", required = true, example = "aircas")
    public String organization;
}
