package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.dto.JwtDTO;
import com.aircas.ptr.foundry.ontology.model.param.UserCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.UserLoginParam;
import com.aircas.ptr.foundry.ontology.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;


    @PostMapping("/login")
    @Operation(summary = "登陆")
    public RestResult userLogin(@RequestBody @Valid UserLoginParam param, HttpServletResponse response) {
        var token = userService.login(param);
        writeTokenHeaders(response, token);
        return RestResult.success();
    }


    @PostMapping("/create")
    @Operation(summary = "用户注册")
    public RestResult userCreate(@RequestBody @Valid UserCreateParam param) {
        userService.create(param);
        return RestResult.success();
    }

    private void writeTokenHeaders(HttpServletResponse response, JwtDTO token) {
        response.setHeader(Constants.ACCESS_TOKEN_HEADER, token.getAccessToken());
        response.setHeader(Constants.REFRESH_TOKEN_HEADER, token.getRefreshToken());
    }

}
