package com.aircas.ptr.foundry.ontology.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.UserCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.UserLoginParam;
import com.aircas.ptr.foundry.ontology.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Api(tags = "用户接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;


    @PostMapping("/login")
    @ApiOperation(value = "登陆")
    public RestResult userLogin(@RequestBody @Valid UserLoginParam param) {
        var token = userService.login(param);
        return RestResult.success();
    }


    @PostMapping("/create")
    @ApiOperation(value = "用户注册")
    public RestResult userCreate(@RequestBody @Valid UserCreateParam param) {
        userService.create(param);
        return RestResult.success();
    }

}
