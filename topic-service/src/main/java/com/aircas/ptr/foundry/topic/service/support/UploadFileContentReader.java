package com.aircas.ptr.foundry.topic.service.support;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class UploadFileContentReader {

    public String read(String fileType, Path path) {
        try {
            return switch (fileType) {
                case "txt" -> readTxt(path);
                case "doc" -> readDoc(path);
                case "docx" -> readDocx(path);
                case "pdf" -> readPdf(path);
                case "xlsx" -> readXlsx(path);
                case "png", "jpg" -> null;
                default -> throw new BusinessException("暂不支持该文件类型", HttpStatus.BAD_REQUEST);
            };
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("文件内容读取失败", HttpStatus.BAD_REQUEST);
        }
    }

    private String readTxt(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private String readDoc(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(input)) {
            return new WordExtractor(document).getText();
        }
    }

    private String readDocx(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input)) {
            return new XWPFWordExtractor(document).getText();
        }
    }

    private String readPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String readXlsx(Path path) throws IOException {
        DataFormatter formatter = new DataFormatter();
        StringBuilder text = new StringBuilder();
        try (InputStream input = Files.newInputStream(path);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
                            text.append('\t');
                        }
                        text.append(formatter.formatCellValue(cell));
                    }
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }
}
