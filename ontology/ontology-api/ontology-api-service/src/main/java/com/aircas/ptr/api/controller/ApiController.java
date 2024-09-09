package com.aircas.ptr.api.controller;

import com.aircas.ptr.api.common.entity.Organization;
import com.aircas.ptr.api.common.params.PtrParams;
import com.aircas.ptr.api.service.IOrganizationService;
import com.aircas.ptr.api.utils.ThreadTaskUtil;
import com.aircas.ptr.foundry.common.base.ApiResult;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ptr")
public class ApiController {

    @Autowired
    private IOrganizationService organizationService;


    /**
     *  可以在这里增加权限信息，对请求数据进行限流，数据权限校验等工作
     * @param params
     * @return
     */
    @ApiOperation(value = "ptr任务接口服务")
    @PostMapping("/task")
    public ApiResult task(@RequestBody PtrParams params){
        // 查询使用者的配置信息
        Organization organization = organizationService.getOrganization(params.getOrganization());

//        int count = ThreadTaskUtil.useThread(organization);
//        if (count==1){
//
//        }
        return ApiResult.fail("您的资源使用紧张");
    }

}
