package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@ApiModel(description = "用户注册参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserCreateParam extends UserLoginParam {

    @ApiModelProperty(name = "picture", value = "用户头像", example = "https://example.com/avatar.jpg")
    private String picture;
}
