package com.aircas.ptr.foundry.ontology.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    String getThumbnailByImage(MultipartFile image) throws Exception;

    String getUrlByImage(MultipartFile image) throws  Exception;

    String getPreviewUrl(MultipartFile file);

}
