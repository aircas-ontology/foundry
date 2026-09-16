package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.JwtDTO;
import com.aircas.ptr.foundry.ontology.model.param.UserCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.UserLoginParam;
import com.aircas.ptr.foundry.ontology.model.po.User;
import com.baomidou.mybatisplus.extension.service.IService;

import jakarta.validation.Valid;

public interface UserService extends IService<User> {


    JwtDTO login(@Valid UserLoginParam param);

    void create(@Valid UserCreateParam param);
}
