package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.UploadFileCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileQueryParam;
import com.aircas.ptr.foundry.topic.model.po.UploadFile;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface UploadFileService extends IService<UploadFile> {

    Long create(UploadFileCreateParam param);

    List<UploadFileVO> listFiles(UploadFileQueryParam param);

    void delete(Long id);

    UploadFileDetailVO get(Long id);
}
