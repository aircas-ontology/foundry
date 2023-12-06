package com.aircas.ptr.foundry.common.util;

import org.springframework.beans.BeanUtils;

public interface CopyUtil {

    /**
     * 复制单个元素
     */
    static <T> T copyBean(Object o, Class<T> clazz) {
        if (o == null)
            return null;
        try {
            T t = clazz.newInstance();
            BeanUtils.copyProperties(o, t);
            return t;
        } catch (Exception e) {
            throw new RuntimeException("copyProperties exception", e);
        }
    }

}
