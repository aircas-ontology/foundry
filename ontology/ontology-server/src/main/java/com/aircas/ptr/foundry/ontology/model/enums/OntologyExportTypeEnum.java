package com.aircas.ptr.foundry.ontology.model.enums;

/**
 * 本体导出类型枚举。
 * <ul>
 *   <li>SCHEMA：仅导出 schema 定义（元数据、属性、关系、分类等），不含实例数据。</li>
 *   <li>INSTANCE：导出 schema 定义及全部实例数据（默认）。</li>
 * </ul>
 */
public enum OntologyExportTypeEnum {

    /** 仅 schema，不含实例数据 */
    SCHEMA,

    /** schema + 实例数据（默认） */
    INSTANCE,
    ;
}
