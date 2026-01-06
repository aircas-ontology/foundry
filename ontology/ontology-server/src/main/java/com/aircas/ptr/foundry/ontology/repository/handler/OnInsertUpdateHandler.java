package com.aircas.ptr.foundry.ontology.repository.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

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
        if (metaObject.hasGetter("latestQueryTime")) {
            this.strictInsertFill(metaObject, "latestQueryTime", Date.class, now);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        if (metaObject.hasGetter("updateTime")) {
            this.strictUpdateFill(metaObject, "updateTime", Date.class, now);
        }
        if (metaObject.hasGetter("latestQueryTime")) {
            this.strictUpdateFill(metaObject, "latestQueryTime", Date.class, now);
        }
    }
}
