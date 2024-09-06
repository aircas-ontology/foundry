package com.aircas.ptr.api.service;

import com.aircas.ptr.api.common.entity.Organization;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IOrganizationService extends IService<Organization> {

    Organization getOrganization(String organization);
}
