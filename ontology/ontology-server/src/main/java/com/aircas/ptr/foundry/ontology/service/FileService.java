package com.aircas.ptr.foundry.ontology.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    String getThumbnailByImage(MultipartFile image) throws Exception;

}
