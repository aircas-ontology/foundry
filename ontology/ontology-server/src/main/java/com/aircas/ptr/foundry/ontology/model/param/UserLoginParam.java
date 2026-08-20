package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@ApiModel(description = "用户登陆参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserLoginParam {

    @ApiModelProperty(name = "username", value = "用户名", example = "admin")
    @NotBlank(message = "username is empty")
    private String username;

    @ApiModelProperty(name = "password", value = "密码", example = "a123456")
    @NotBlank(message = "password is empty")
    private String password;
}
