package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyBuildFromDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyBuildFromDatasourceResultVO;

/**
 * 「数据源构建对象」向导第④步：把外部库勾选的列落成一个可查询的本体对象。
 */
public interface OntologyBuildFromDatasourceService {

    /**
     * 用外部数据源若干张表的勾选列创建一个本体对象，并在数据湖该空间的 schema 下建对应实体表、
     * 写好主子表列映射、把属性的数据源字段回填。
     *
     * <p>规则：主表属性 {@code storageGroup=main}、实体表名取对象 apiName；子表属性
     * {@code storageGroup} 用子表表名、实体表名同名。主子表的关联列就是用户配置的那一列本身，
     * 不再额外加平台的 {@code {主表名}_id} 列——所以这段不能复用 {@code autoBindDatasource}
     * （它把关联列写死了），但 {@code genericQuery} 是按映射行里的任意列名 JOIN 的，照样查得到。</p>
     *
     * <p>外键列不要求勾成属性：属性 apiName 在一个对象内必须唯一，主子表同名外键列（如
     * {@code ship.ship_id ↔ ship_weapon.ship_id}）一旦两侧都勾成属性就必然撞名。
     * 没勾成属性的一侧只建物理列（管道列）。</p>
     *
     * <p>只建结构不搬数据：实体表建出来是空的，外部库的实例数据需要另做灌入。数据管道事件也不在这里发，
     * 由前端照画布的做法再调 {@code POST /property/notify_build_pipeline}。</p>
     *
     * @param param 向导汇总的建单入参
     * @return 新建对象的 uniqueIdentifier 与本次建成的属性数、实体表名、映射行数
     */
    OntologyBuildFromDatasourceResultVO buildObject(OntologyBuildFromDatasourceParam param);
}
