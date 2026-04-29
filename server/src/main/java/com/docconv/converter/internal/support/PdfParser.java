package com.docconv.converter.internal.support.documentparser;

import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;
import com.docconv.converter.support.exception.Errors;
import com.docconv.converter.support.kit.FileKit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfParser implements IDocumentParser {

    private final FileKit fileKit;

    /// 解析 PDF 格式的文档
    ///
    /// 使用 OpenDataLoader PDF SDK 提取 PDF 中的文本内容
    ///
    /// @param document 上传的 PDF 文档文件
    /// @return 解析结果，包含解析后的 Markdown 内容
    @Override
    public ConvertDocumentParseResult parse(UploadFile document) {
        var workingDir = fileKit.createTmpDir("pdf-parse");
        Path tempPdfFile = null;

        try {
            // OpenDataLoader 需要文件路径 — 写入临时文件
            tempPdfFile = Files.createTempFile(workingDir.toPath(), "pdf-parse-", ".pdf");
            try (var out = new FileOutputStream(tempPdfFile.toFile())) {
                var bytes = document.getInputStream().readAllBytes();
                out.write(bytes);
            }

            String content = extractFromPdf(tempPdfFile, workingDir);

            var result = new ConvertDocumentParseResult();
            result.setContent(content);
            return result;
        } catch (Exception e) {
            if (e instanceof com.docconv.converter.support.exception.AppException) {
                throw (RuntimeException) e;
            }
            throw Errors.PARSER_EXECUTION_ERROR.toException(e, "PDF 解析失败: {}", e.getMessage());
        } finally {
            if (tempPdfFile != null) {
                try {
                    Files.deleteIfExists(tempPdfFile);
                } catch (Exception e) {
                    log.warn("Failed to delete temp PDF file: {}", tempPdfFile, e);
                }
            }
            try {
                cn.hutool.core.io.FileUtil.del(workingDir);
            } catch (Exception e) {
                log.warn("Failed to delete working dir: {}", workingDir, e);
            }
        }
    }

    private String extractFromPdf(Path pdfPath, File workingDir) throws Exception {
        var config = new Config();
        config.setOutputFolder(workingDir.getAbsolutePath());
        config.setGenerateMarkdown(true);
        config.setGeneratePDF(false);
        config.setGenerateHtml(false);

        try {
            OpenDataLoaderPDF.processFile(pdfPath.toAbsolutePath().toString(), config);
        } finally {
            OpenDataLoaderPDF.shutdown();
        }

        // 查找生成的 markdown 文件
        var pdfFileName = pdfPath.getFileName().toString();
        var markdownFileName = pdfFileName.replaceAll("\\.pdf$", ".md");
        var markdownFile = new File(workingDir, markdownFileName);

        // 如果找不到，尝试在 output 文件夹中查找
        var outputDir = new File(workingDir, "output");
        if (!markdownFile.exists() && outputDir.exists()) {
            var outputMarkdownFile = new File(outputDir, markdownFileName);
            if (outputMarkdownFile.exists()) {
                markdownFile = outputMarkdownFile;
            }
        }

        if (!markdownFile.exists()) {
            throw Errors.PARSER_EXECUTION_ERROR.toException("PDF 解析失败：未生成 Markdown 文件");
        }

        var markdown = cn.hutool.core.io.FileUtil.readUtf8String(markdownFile);
        // 移除图片引用，MVP 只保留文本
        return markdown.replaceAll("!\\[.*?\\]\\(.*?\\)", "");
    }
}
