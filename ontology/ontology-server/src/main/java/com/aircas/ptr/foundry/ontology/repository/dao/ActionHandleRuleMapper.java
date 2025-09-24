package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.model.po.ActionHandleRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @interfaceName: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 18:37
 * @version: 1.0
 * @description: 行为执行规则
 */
@Mapper
public interface ActionHandleRuleMapper extends BaseMapper<ActionHandleRule> {

    ActionHandleRule selectByActionId(String actionId);


    List<ActionHandleRule> queryRulesById(@Param("ids") List<Long> ids);


    int updateRulesById(ActionHandleRule rule);


}
