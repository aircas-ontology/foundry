package com.aircas.ptr.foundry.ontology.repository.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;

public class OnInsertUpdateHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        if (metaObject.hasGetter("createTime")) {
            this.strictInsertFill(metaObject, "createTime", Date.class, Date.from(now));
        }
        if (metaObject.hasGetter("updateTime")) {
            insertUpdateTime(metaObject, now);
        }
        if (metaObject.hasGetter("latestQueryTime")) {
            this.strictInsertFill(metaObject, "latestQueryTime", Date.class, Date.from(now));
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Instant now = Instant.now();
        if (metaObject.hasGetter("updateTime")) {
            updateUpdateTime(metaObject, now);
        }
        if (metaObject.hasGetter("latestQueryTime")) {
            this.strictUpdateFill(metaObject, "latestQueryTime", Date.class, Date.from(now));
        }
    }

    private void insertUpdateTime(MetaObject metaObject, Instant now) {
        if (metaObject.getGetterType("updateTime") == Timestamp.class) {
            this.strictInsertFill(metaObject, "updateTime", Timestamp.class, Timestamp.from(now));
            return;
        }
        this.strictInsertFill(metaObject, "updateTime", Date.class, Date.from(now));
    }

    private void updateUpdateTime(MetaObject metaObject, Instant now) {
        if (metaObject.getGetterType("updateTime") == Timestamp.class) {
            this.strictUpdateFill(metaObject, "updateTime", Timestamp.class, Timestamp.from(now));
            return;
        }
        this.strictUpdateFill(metaObject, "updateTime", Date.class, Date.from(now));
    }
}
