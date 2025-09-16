package com.aircas.ptr.foundry.ontology.entity.service;

import com.aircas.ptr.foundry.ontology.entity.model.dto.BatchImportDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {
    
    public BatchImportDTO parseExcel(MultipartFile file) throws IOException {
        Workbook workbook = WorkbookFactory.create(file.getInputStream());
        BatchImportDTO batchData = new BatchImportDTO();
        
        // 读取节点sheet
        Sheet nodeSheet = workbook.getSheet("Nodes");
        List<BatchImportDTO.NodeDTO> nodes = new ArrayList<>();
        for (Row row : nodeSheet) {
            if (row.getRowNum() == 0) continue; // 跳过表头
            
            BatchImportDTO.NodeDTO node = new BatchImportDTO.NodeDTO();
            node.setName(row.getCell(0).getStringCellValue());
            node.setDescription(row.getCell(1).getStringCellValue());
            nodes.add(node);
        }
        
        // 读取关系sheet
        Sheet relationSheet = workbook.getSheet("Relations");
        List<BatchImportDTO.RelationDTO> relations = new ArrayList<>();
        for (Row row : relationSheet) {
            if (row.getRowNum() == 0) continue; // 跳过表头
            
            BatchImportDTO.RelationDTO relation = new BatchImportDTO.RelationDTO();
            relation.setFromNodeName(row.getCell(0).getStringCellValue());
            relation.setToNodeName(row.getCell(1).getStringCellValue());
            relation.setRelationType(row.getCell(2).getStringCellValue());
            relation.setDescription(row.getCell(3).getStringCellValue());
            relations.add(relation);
        }
        
        batchData.setNodes(nodes);
        batchData.setRelations(relations);
        return batchData;
    }
    
    public byte[] generateExcelTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        
        // 创建节点模板
        Sheet nodeSheet = workbook.createSheet("Nodes");
        Row nodeHeader = nodeSheet.createRow(0);
        nodeHeader.createCell(0).setCellValue("Name");
        nodeHeader.createCell(1).setCellValue("Description");
        
        // 创建关系模板
        Sheet relationSheet = workbook.createSheet("Relations");
        Row relationHeader = relationSheet.createRow(0);
        relationHeader.createCell(0).setCellValue("FromNodeName");
        relationHeader.createCell(1).setCellValue("ToNodeName");
        relationHeader.createCell(2).setCellValue("RelationType");
        relationHeader.createCell(3).setCellValue("Description");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
} 