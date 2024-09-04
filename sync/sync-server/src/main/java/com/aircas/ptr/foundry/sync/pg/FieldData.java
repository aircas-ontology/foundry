package com.aircas.ptr.foundry.sync.pg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 列值信息定义
 * 
 * @author yibo.tang
 * @date 2021-04-25 14:50:14
 * @since 1.0
 *
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FieldData {
	/** 列名 */
	private String name;
	/** 类型 */
	private String dataType;
	/** 列值，这里用String存储 */
	private String value;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FieldData data = (FieldData) o;
		return Objects.equals(name, data.name) && Objects.equals(dataType, data.dataType)
				&& Objects.equals(value, data.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, dataType, value);
	}
}
