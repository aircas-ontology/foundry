package com.aircas.ptr.foundry.ontology.intercepter;

import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.context.UserContextHolder;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.dto.UserContextDTO;
import com.aircas.ptr.foundry.ontology.model.po.User;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.UserMapper;
import com.aircas.ptr.foundry.ontology.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String accessToken = request.getHeader(Constants.ACCESS_TOKEN_HEADER);
        if (!jwtUtil.validateAccessToken(accessToken)) {
            throw new BusinessException("未认证或token无效", ResultCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        UserContextDTO userContext = jwtUtil.parseUserContext(accessToken);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, userContext.getUsername()));

        PreconditionUtils.checkNotNull(user, "用户不存在");
        UserContextHolder.set(userContext);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
