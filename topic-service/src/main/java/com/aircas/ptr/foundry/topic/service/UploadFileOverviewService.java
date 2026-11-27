package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.UploadFileOverview;
import com.baomidou.mybatisplus.spring.service.IService;

public interface UploadFileOverviewService extends IService<UploadFileOverview> {

    Long create(UploadFileOverviewCreateParam param);

    void update(UploadFileOverviewUpdateParam param);
}
