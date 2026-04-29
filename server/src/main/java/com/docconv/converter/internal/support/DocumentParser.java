package com.docconv.converter.internal.support.documentparser;

import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;
import com.docconv.converter.support.exception.Errors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 文档解析器（委派）
///
/// 根据文件类型将解析任务委派给具体的解析器
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentParser implements IDocumentParser {

    /// Word 格式文档解析器
    private final WordParser wordParser;

    /// PDF 格式文档解析器
    private final PdfParser pdfParser;

    /// 解析文档，根据文件类型委托给具体的解析器
    ///
    /// @param document 上传的文档文件
    /// @return 解析结果，包含解析后的 Markdown 内容
    @Override
    public ConvertDocumentParseResult parse(UploadFile document) {
        var extName = extractExtension(document.getFilename());

        return switch (extName) {
            case "docx" -> wordParser.parse(document);
            case "pdf" -> pdfParser.parse(document);
            default -> throw Errors.UNSUPPORTED_FORMAT.toException("不支持的文件类型: " + extName);
        };
    }

    static String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw Errors.UNSUPPORTED_FORMAT.toException("文件名为空");
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            throw Errors.UNSUPPORTED_FORMAT.toException("无法识别文件扩展名: " + filename);
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
