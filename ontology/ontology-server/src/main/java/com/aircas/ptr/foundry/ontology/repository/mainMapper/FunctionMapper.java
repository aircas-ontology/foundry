package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FunctionMapper extends BaseMapper<Function> {

    /**
     * 版本分组分页：按 api 分组，每组仅取版本号最大的一条记录
     *
     * @param page            分页参数
     * @param ontologySpaceId 本体空间 id，可空
     * @param displayName     函数名称模糊搜索，可空
     * @param type            函数类型，可空
     * @param publishStatus   发布状态（0 未发布 / 1 已发布），可空
     * @param startDate       创建开始日期 yyyy-MM-dd，可空
     * @param endDate         创建结束日期 yyyy-MM-dd，可空
     * @return 每个 api 最新版本的分页结果
     */
    Page<Function> pageLatestVersion(Page<Function> page,
                                     @Param("ontologySpaceId") Integer ontologySpaceId,
                                     @Param("displayName") String displayName,
                                     @Param("type") FunctionTypeEnum type,
                                     @Param("publishStatus") Integer publishStatus,
                                     @Param("startDate") String startDate,
                                     @Param("endDate") String endDate);
}