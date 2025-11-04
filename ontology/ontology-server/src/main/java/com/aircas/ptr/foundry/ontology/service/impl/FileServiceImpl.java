package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.service.FileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class FileServiceImpl implements FileService {


    @Value("${minio.bucketName}")
    private String bucketName;

    private static final Integer THUMBNAIL_WIDTH = 50;

    private static final Integer THUMBNAIL_HEIGHT = 50;


    @Resource
    private MinioClient minioClient;

    @Override
    public String getThumbnailByImage(MultipartFile image) throws Exception {
        //check image file
        PreconditionUtils.checkArgument(!image.isEmpty(), "image is empty");
        String contentType = image.getContentType();
        PreconditionUtils.checkArgument(contentType != null && contentType.startsWith("image/"), "not image file");
        // Generate thumbnail
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(image.getInputStream())
                .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                .outputFormat("jpg")
                .toOutputStream(outputStream);
        ByteArrayInputStream thumbnailInputStream = new ByteArrayInputStream(outputStream.toByteArray());
        //save to minio
        String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(thumbnailInputStream, outputStream.size(), -1)
                        .contentType("image/jpeg")
                        .build()
        );
        //get url
        String imageUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );
        return imageUrl.substring(0, imageUrl.indexOf("?"));
    }
}
