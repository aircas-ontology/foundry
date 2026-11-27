package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.UploadFileOverview;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileOverviewStatVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface UploadFileOverviewService extends IService<UploadFileOverview> {

    Long create(UploadFileOverviewCreateParam param);

    void update(UploadFileOverviewUpdateParam param);

    List<UploadFileOverviewStatVO> listStats();
}
