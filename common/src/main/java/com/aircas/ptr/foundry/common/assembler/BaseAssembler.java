package com.aircas.ptr.foundry.common.assembler;

import com.aircas.ptr.foundry.common.util.CopyUtil;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基础的dto与bo转换类
 *
 * @author Administrator
 */
public abstract class BaseAssembler<BO, DTO> {

    private final Class<BO> boClass;
    private final Class<DTO> dtoClass;

    public BaseAssembler(Class<BO> boClass, Class<DTO> dtoClass) {

        this.boClass = boClass;
        this.dtoClass = dtoClass;
    }

    public DTO toDTO(BO bo) {
        return CopyUtil.copyBean(bo, dtoClass);
    }

    public BO toBO(DTO dto) {
        return CopyUtil.copyBean(dto, boClass);
    }

    public List<DTO> toDTOs(List<BO> bos) {
        if (CollectionUtils.isEmpty(bos)) {
            return Collections.emptyList();
        }
        return bos.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
