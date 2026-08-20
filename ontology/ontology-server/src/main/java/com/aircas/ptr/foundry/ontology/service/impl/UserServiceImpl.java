package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.model.dto.JwtDTO;
import com.aircas.ptr.foundry.ontology.model.dto.UserContextDTO;
import com.aircas.ptr.foundry.ontology.model.param.UserCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.UserLoginParam;
import com.aircas.ptr.foundry.ontology.model.po.User;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.UserMapper;
import com.aircas.ptr.foundry.ontology.service.UserService;
import com.aircas.ptr.foundry.ontology.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;

    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public JwtDTO login(UserLoginParam param) {
        var user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, param.getUsername()));
        PreconditionUtils.checkArgument(user != null, "用户名或密码错误", ResultCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        PreconditionUtils.checkArgument(passwordEncoder.matches(param.getPassword(), user.getPassword()),
                "用户名或密码错误", ResultCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);

        var userContext = UserContextDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .build();
        return jwtUtil.generateTokens(userContext);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    @Override
    public void create(UserCreateParam param) {
        var existUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, param.getUsername()));
        PreconditionUtils.checkIsNull(existUser, "用户名已存在", HttpStatus.CONFLICT);

        var user = User.builder()
                .username(param.getUsername())
                .password(passwordEncoder.encode(param.getPassword()))
                .picture(param.getPicture())
                .build();
        userMapper.insert(user);
    }
}
