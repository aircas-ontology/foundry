package com.aircas.ptr.api.service.impl;

import com.aircas.ptr.api.common.entity.Organization;
import com.aircas.ptr.api.mapper.OrganizationMapper;
import com.aircas.ptr.api.service.IOrganizationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class OrganizationServiceImpl extends ServiceImpl<OrganizationMapper,Organization> implements IOrganizationService {

    @Override
    public Organization getOrganization(String organization) {
        return this.lambdaQuery().eq(Organization::getOrganization,organization).one();
    }
}
