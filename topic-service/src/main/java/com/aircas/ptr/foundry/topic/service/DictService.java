package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.DictCreateParam;
import com.aircas.ptr.foundry.topic.model.param.DictQueryParam;
import com.aircas.ptr.foundry.topic.model.param.DictUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.Dict;
import com.aircas.ptr.foundry.topic.model.vo.DictCreateVO;
import com.aircas.ptr.foundry.topic.model.vo.DictItemVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface DictService extends IService<Dict> {

    DictCreateVO create(DictCreateParam param);

    List<DictItemVO> listByGroup(DictQueryParam param);

    void update(DictUpdateParam param);
}
