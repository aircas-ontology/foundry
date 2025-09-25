package com.aircas.ptr.foundry.ontology.repository.dao;

import com.aircas.ptr.foundry.ontology.model.po.ActionHandleCommitFlashMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @interfaceName: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 18:37
 * @version: 1.0
 * @description: 行为执行规则
 */
@Mapper
public interface ActionHandleCommitFlashMermoryMapper extends BaseMapper<ActionHandleCommitFlashMemory> {

    ActionHandleCommitFlashMemory getActionFlashMemoryByActionAndPrimary(@Param("actionId") long actionId, @Param("primaryKey") String primaryKey);

    int updateActionDataLogById(ActionHandleCommitFlashMemory memory);


}
