package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.dto.JwtDTO;
import com.aircas.ptr.foundry.ontology.model.param.UserCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.UserLoginParam;
import com.aircas.ptr.foundry.ontology.model.po.User;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.UserMapper;
import com.aircas.ptr.foundry.ontology.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;

    @Override
    public JwtDTO login(UserLoginParam param) {
        return null;
    }

    @Override
    public void create(UserCreateParam param) {

    }
}
