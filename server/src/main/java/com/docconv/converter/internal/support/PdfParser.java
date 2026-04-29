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
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfParser implements IDocumentParser {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");

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
            // 删除工作目录（图片已内嵌为 Base64）
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

        // 将图片引用转换为 Base64 内嵌格式
        markdown = embedImagesAsBase64(markdown, workingDir);

        return markdown;
    }

    /// 将 Markdown 中的图片引用转换为 Base64 内嵌格式
    ///
    /// @param markdown 原始 Markdown 内容
    /// @param workingDir 图片所在的工作目录
    /// @return 包含 Base64 内嵌图片的 Markdown 内容
    private String embedImagesAsBase64(String markdown, File workingDir) {
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String altText = matcher.group(1);
            String imagePath = matcher.group(2);

            // 只处理相对路径的图片（OpenDataLoader 提取的图片）
            if (!imagePath.startsWith("/") && !imagePath.startsWith("http")) {
                File imageFile = new File(workingDir, imagePath);
                if (imageFile.exists()) {
                    try {
                        String mimeType = detectMimeType(imageFile.getName());
                        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imageFile.toPath()));
                        String dataUri = String.format("![%s](data:%s;base64,%s)", altText, mimeType, base64);
                        matcher.appendReplacement(result, Matcher.quoteReplacement(dataUri));
                    } catch (Exception e) {
                        log.warn("Failed to embed image: {}", imagePath, e);
                        // 如果嵌入失败，保留原引用
                        matcher.appendReplacement(result, matcher.group(0));
                    }
                } else {
                    log.warn("Image file not found: {}", imageFile.getAbsolutePath());
                    matcher.appendReplacement(result, matcher.group(0));
                }
            } else {
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /// 根据文件扩展名检测 MIME 类型
    private String detectMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/png";
    }
}
