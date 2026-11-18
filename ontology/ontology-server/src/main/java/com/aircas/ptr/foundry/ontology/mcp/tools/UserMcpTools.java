package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.dto.JwtDTO;
import com.aircas.ptr.foundry.ontology.model.param.UserCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.UserLoginParam;
import com.aircas.ptr.foundry.ontology.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 用户 MCP 工具（对应 {@code UserController}，REST 前缀 /user）。
 *
 * <p>覆盖登录与注册两个端点。REST 登录把 token 写在响应头 access-token / refresh-token；
 * MCP 无响应头可写，login 工具直接返回 access-token 头的值（带 Bearer 前缀的 token 字符串）。
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class UserMcpTools implements McpTools {

    private final UserService userService;

    @Tool(description = """
            用户登录（对应 REST 端点 POST /user/login）。
            当需要获取平台访问令牌（access-token）以便后续调用需要鉴权的接口时使用。
            参数：username 必填，用户名；password 必填，密码（明文传入，服务端按 BCrypt 校验）。
            返回 token 字符串：即 REST 登录响应头 access-token 的值，带 "Bearer " 前缀（形如 "Bearer eyJ..."），
            后续请求把它原样放进 access-token 请求头即可；不返回整个响应对象，也不返回 refresh-token。
            用户名或密码错误时抛错（401 口径的中文提示"用户名或密码错误"）。
            注意：token 是敏感凭据，不要在无关输出中扩散。
            """)
    public String userLogin(
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "密码") String password) {
        requireText(username, "username");
        requireText(password, "password");
        UserLoginParam param = new UserLoginParam();
        param.setUsername(username);
        param.setPassword(password);
        JwtDTO token = userService.login(param);
        // REST 端点把该值写入响应头 access-token（JwtUtil 生成时已带 Bearer 前缀），MCP 直接返回该字符串
        return token.getAccessToken();
    }

    @Tool(description = """
            用户注册（对应 REST 端点 POST /user/create）。
            当需要为平台创建新用户账号时使用。
            参数：username 必填，用户名（不能与已有用户重复，重复时报"用户名已存在"）；
            password 必填，密码（服务端 BCrypt 加密后落库）；picture 可选，用户头像 URL。
            副作用：向用户表插入一条新账号记录。成功返回中文结果说明。
            """)
    public String userCreate(
            @ToolParam(description = "用户名（不能与已有用户重复）") String username,
            @ToolParam(description = "密码") String password,
            @ToolParam(description = "用户头像 URL，可为空", required = false) String picture) {
        requireText(username, "username");
        requireText(password, "password");
        UserCreateParam param = new UserCreateParam();
        param.setUsername(username);
        param.setPassword(password);
        param.setPicture(picture);
        userService.create(param);
        return "用户注册成功：" + username;
    }

    private void requireText(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }
}