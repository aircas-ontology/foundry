package com.aircas.ptr.foundry.topic.repository.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

/**
 * 插入时填充 createTime / updateTime，更新时填充 updateTime。
 * 实体字段需标注 {@code @TableField(fill = FieldFill.INSERT)} 或 {@code INSERT_UPDATE}。
 */
public class OnInsertUpdateHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        if (metaObject.hasGetter("createTime")) {
            this.strictInsertFill(metaObject, "createTime", Date.class, now);
        }
        if (metaObject.hasGetter("updateTime")) {
            this.strictInsertFill(metaObject, "updateTime", Date.class, now);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasGetter("updateTime")) {
            this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
        }
    }
}
