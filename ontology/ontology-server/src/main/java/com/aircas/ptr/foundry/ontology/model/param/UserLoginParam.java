package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "用户登陆参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserLoginParam {

    @Schema(name = "username", description = "用户名", example = "admin")
    @NotBlank(message = "username is empty")
    private String username;

    @Schema(name = "password", description = "密码", example = "a123456")
    @NotBlank(message = "password is empty")
    private String password;
}
