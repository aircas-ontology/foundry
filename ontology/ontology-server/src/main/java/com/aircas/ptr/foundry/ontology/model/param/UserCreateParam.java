package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Schema(description = "用户注册参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class UserCreateParam extends UserLoginParam {

    @Schema(name = "picture", description = "用户头像", example = "https://example.com/avatar.jpg")
    private String picture;
}
