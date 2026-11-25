package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DictStatusEnum;
import com.aircas.ptr.foundry.topic.model.param.DictCreateParam;
import com.aircas.ptr.foundry.topic.model.param.DictUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.Dict;
import com.aircas.ptr.foundry.topic.model.vo.DictCreateVO;
import com.aircas.ptr.foundry.topic.model.vo.DictItemVO;
import com.aircas.ptr.foundry.topic.repository.DictMapper;
import com.aircas.ptr.foundry.topic.service.DictService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {

    @Transactional
    @Override
    public DictCreateVO create(DictCreateParam param) {
        String creator = StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim();
        Dict dict = Dict.builder()
                .dictName(param.getDictName())
                .groupCode(param.getGroupCode().trim())
                .groupName(param.getGroupName())
                .status(DictStatusEnum.NORMAL.getCode())
                .creator(creator)
                .build();
        save(dict);
        return DictCreateVO.builder()
                .id(dict.getId())
                .groupCode(dict.getGroupCode())
                .build();
    }

    @Override
    public List<DictItemVO> listByGroup(String groupCode) {
        List<Dict> dicts = list(new LambdaQueryWrapper<Dict>()
                .select(Dict::getId, Dict::getDictName)
                .eq(Dict::getGroupCode, groupCode)
                .eq(Dict::getStatus, DictStatusEnum.NORMAL.getCode())
                .orderByAsc(Dict::getId));
        return dicts.stream().map(this::toItem).toList();
    }

    @Transactional
    @Override
    public void update(DictUpdateParam param) {
        Dict dict = getById(param.getId());
        PreconditionUtils.checkNotNull(dict, "字典不存在", HttpStatus.BAD_REQUEST);
        dict.setDictName(param.getDictName());
        dict.setGroupCode(param.getGroupCode());
        dict.setGroupName(param.getGroupName());
        dict.setStatus(param.getStatus());
        updateById(dict);
    }

    private DictItemVO toItem(Dict dict) {
        return DictItemVO.builder()
                .id(dict.getId())
                .dictName(dict.getDictName())
                .build();
    }
}
