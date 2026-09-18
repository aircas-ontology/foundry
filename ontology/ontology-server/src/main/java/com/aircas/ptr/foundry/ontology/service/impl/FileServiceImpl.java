package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.service.FileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;

@Service
@Slf4j
public class FileServiceImpl implements FileService {


    @Value("${minio.bucketName}")
    private String bucketName;

    private static final Integer THUMBNAIL_WIDTH = 64;

    private static final Integer THUMBNAIL_HEIGHT = 64;


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
        var imageUrl = saveToMinio(image, thumbnailInputStream);
        return imageUrl.substring(0, imageUrl.indexOf("?"));
    }

    @Override
    public String getUrlByImage(MultipartFile image) throws Exception {
        //check image file
        PreconditionUtils.checkArgument(!image.isEmpty(), "image is empty");
        String contentType = image.getContentType();
        PreconditionUtils.checkArgument(contentType != null && contentType.startsWith("image/"), "not image file");
        //save to minio
        var imageUrl = saveToMinio(image, image.getInputStream());
        return imageUrl.substring(0, imageUrl.indexOf("?"));
    }


    @SneakyThrows
    @Override
    public String getPreviewUrl(MultipartFile file) {
        //save to minio
        var originalFileName = URLEncoder.encode(file.getOriginalFilename(), "UTF-8");
        String fileName = System.currentTimeMillis() + "_" + originalFileName;
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(getContentType(originalFileName))
                        .build()
        );
        //get url
        String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );
        return url.substring(0, url.indexOf("?"));
    }


    private String getContentType(String fileName) {
        // 获取文件后缀名
        int dotIndex = fileName.lastIndexOf('.');
        String extension = dotIndex == -1 ? "" : fileName.substring(dotIndex + 1).toLowerCase();

        // 如果没有后缀名，直接返回application/octet-stream
        if (extension.isEmpty()) {
            return "application/octet-stream";
        }

        // 使用switch case根据文件后缀名生成Content-Type
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "tiff":
                return "image/tiff";
            case "txt":
            case "sql":
            case "csv":
            case "json":
                return "text/plain";
            case "pdf":
                return "application/pdf";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mkv":
                return "video/x-matroska";
            case "mov":
                return "video/quicktime";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            default:
                return "application/octet-stream";
        }
    }

    private String saveToMinio(MultipartFile image, InputStream inputStream) throws Exception {
        //save to minio
        String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(inputStream, inputStream.available(), -1)
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
        return imageUrl;
    }
}
