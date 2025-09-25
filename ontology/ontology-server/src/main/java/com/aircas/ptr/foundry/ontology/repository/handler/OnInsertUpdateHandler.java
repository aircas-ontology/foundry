package com.aircas.ptr.foundry.ontology.repository.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

public class OnInsertUpdateHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictInsertFill(metaObject,"createTime",Date.class,now);
        this.strictInsertFill(metaObject,"updateTime",Date.class,now);

    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Date now = new Date();
        this.strictUpdateFill(metaObject,"updateTime",Date.class,now);
    }
}
