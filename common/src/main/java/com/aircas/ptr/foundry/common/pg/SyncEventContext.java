package com.aircas.ptr.foundry.common.pg;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 事件Event的上下文信息
 * 
 * @author yibo.tang
 * @date 2021-04-25 17:57:13
 * @since 1.0
 *
 */
@Data
public class SyncEventContext {
	/** 日志序列号LogSequenceNumber */
    private long lsn;
    /** 逻辑复制槽名 */
    private transient String slotName;
    /** schema名 */
    private String schema;
    /** table名 */
    private String table;
    /** 字段元信息 */
    private List<FieldMeta> fields;
    /** Event事件类型 */
    private EventTypeEnum eventType;
    /** 数据列表 */
    private List<FieldData> datas = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SyncEventContext event = (SyncEventContext) o;
        return Objects.equals(schema, event.schema) &&
                Objects.equals(table, event.table) &&
                eventType == event.eventType &&
                Objects.equals(datas, event.datas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, table, eventType, datas);
    }

    @Override
    public String toString() {
        return "EventContext {" +
                "schema='" + schema + '\'' +
                ", table='" + table + '\'' +
                ", eventType=" + eventType +
                ", dataList=" + datas +
                '}';
    }
}
