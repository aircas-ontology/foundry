package com.aircas.ptr.foundry.sync.pg;

/**
 * 支持的事件Event类型定义
 * 
 * @author yibo.tang
 * @date 2021-04-25 14:49:45
 * @since 1.0
 * @ClassName EventTypeEnum
 * @Description EventTypeEnum
 * @Version 1.0
 **/
public enum EventTypeEnum{

    /**
     * 开始
     */
    BEGIN,
    /**
     * 提交
     */
    COMMIT,
    /**
     * 插入
     */
    INSERT,
    /**
     * 更新
     */
    UPDATE,
    /**
     * 删除
     */
    DELETE,
    /**
     * 清空
     */
    TRUNCATE;

    public static EventTypeEnum getEventType(String event) {
        if (EventTypeEnum.INSERT.name().equalsIgnoreCase(event)) {
            return EventTypeEnum.INSERT;
        } else if (EventTypeEnum.UPDATE.name().equalsIgnoreCase(event)) {
            return EventTypeEnum.UPDATE;
        } else if (EventTypeEnum.DELETE.name().equalsIgnoreCase(event)) {
            return EventTypeEnum.DELETE;
        } else if (EventTypeEnum.TRUNCATE.name().equalsIgnoreCase(event)) {
            return EventTypeEnum.TRUNCATE;
        } else if (EventTypeEnum.BEGIN.name().equalsIgnoreCase(event)) {
            return EventTypeEnum.BEGIN;
        } else if (EventTypeEnum.COMMIT.name().equalsIgnoreCase(event)) {
            return EventTypeEnum.COMMIT;
        } else {
            throw new IllegalArgumentException("unsupported event:" + event);
        }
    }
}
