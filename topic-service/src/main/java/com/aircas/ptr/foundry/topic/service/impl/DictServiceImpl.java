package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.topic.model.enums.DictStatusEnum;
import com.aircas.ptr.foundry.topic.model.param.DictCreateParam;
import com.aircas.ptr.foundry.topic.model.param.DictQueryParam;
import com.aircas.ptr.foundry.topic.model.param.DictUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.Dict;
import com.aircas.ptr.foundry.topic.model.vo.DictCreateVO;
import com.aircas.ptr.foundry.topic.model.vo.DictItemVO;
import com.aircas.ptr.foundry.topic.repository.DictMapper;
import com.aircas.ptr.foundry.topic.service.DictService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {

    @Transactional
    @Override
    public DictCreateVO create(DictCreateParam param) {
        String dictCode = "D" + SnowflakeIdUtil.get();
        String groupCode = "G" + SnowflakeIdUtil.get();
        Dict dict = Dict.builder()
                .dictCode(dictCode)
                .dictName(param.getDictName())
                .groupCode(groupCode)
                .groupName(param.getGroupName())
                .status(DictStatusEnum.NORMAL.getCode())
                .creator(param.getCreator())
                .build();
        save(dict);
        return DictCreateVO.builder()
                .dictCode(dictCode)
                .groupCode(groupCode)
                .build();
    }

    @Override
    public List<DictItemVO> listByGroup(DictQueryParam param) {
        List<Dict> dicts = list(new LambdaQueryWrapper<Dict>()
                .select(Dict::getDictCode, Dict::getDictName)
                .eq(Dict::getGroupCode, param.getGroupCode())
                .eq(Dict::getStatus, DictStatusEnum.NORMAL.getCode())
                .orderByAsc(Dict::getDictCode));
        return dicts.stream().map(this::toItem).toList();
    }

    @Transactional
    @Override
    public void update(DictUpdateParam param) {
        Dict dict = getById(param.getDictCode());
        PreconditionUtils.checkNotNull(dict, "字典不存在", HttpStatus.BAD_REQUEST);
        dict.setDictName(param.getDictName());
        dict.setGroupCode(param.getGroupCode());
        dict.setGroupName(param.getGroupName());
        dict.setStatus(param.getStatus());
        updateById(dict);
    }

    private DictItemVO toItem(Dict dict) {
        return DictItemVO.builder()
                .dictCode(dict.getDictCode())
                .dictName(dict.getDictName())
                .build();
    }
}
