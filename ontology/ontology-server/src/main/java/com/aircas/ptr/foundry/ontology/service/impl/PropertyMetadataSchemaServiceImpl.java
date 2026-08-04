package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.PropertyMetadataSchema;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.PropertyMetadataSchemaMapper;
import com.aircas.ptr.foundry.ontology.service.PropertyMetadataSchemaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PropertyMetadataSchemaServiceImpl extends ServiceImpl<PropertyMetadataSchemaMapper, PropertyMetadataSchema> implements PropertyMetadataSchemaService {
}
