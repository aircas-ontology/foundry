package com.aircas.ptr.foundry.ontology.application.service.impl;


import com.aircas.ptr.foundry.ontology.application.service.OwlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;


@Service
@RequiredArgsConstructor
public class OwlServiceImpl implements OwlService {


    //根据uri获取资源
    @Override
    public String getResource(URI uri) {
        return null;
    }
}
