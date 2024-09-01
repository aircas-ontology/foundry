package com.aircas.ptr.foundry.sync.pg;

import com.aircas.ptr.foundry.common.exception.DmException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 表的meta数据描述定义
 *
 * <pre>
 * 1. shema名
 * 2. table名
 * 3. 列信息
 * </pre>
 * 
 * @author yibo.tang
 * @date 2021-04-25 14:48:09
 * @since 1.0
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TableMeta {
	/** schema名 */
    private String          schema;
    /** table名 */
    private String          table;
    /** field列表 */
    private List<FieldMeta> fields = new ArrayList<>();

    public void addFieldMeta(FieldMeta fieldMeta) {
        this.fields.add(fieldMeta);
    }
    
    public FieldMeta getFieldMetaByName(String name) {
        for (FieldMeta meta : fields) {
            if (meta.getColumnName().equalsIgnoreCase(name)) {
                return meta;
            }
        }
        throw new DmException("unknow column : " + name);
    }

    public List<FieldMeta> findPrimaryFields() {
        List<FieldMeta> primarys = new ArrayList<>();
        for (FieldMeta meta : fields) {
            if (meta.isPrimaryKey()) {
                primarys.add(meta);
            }
        }
        return primarys;
    }
}
