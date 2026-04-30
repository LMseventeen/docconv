package com.docconv.converter.internal.support.documentparser;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.docconv.converter.config.PandocConfig;
import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;
import com.docconv.support.exception.Errors;
import com.docconv.support.kit.FileKit;
import com.docconv.support.process.CommandExecutor;

import cn.hutool.core.io.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WordParser implements IDocumentParser {

    private final FileKit fileKit;
    private final PandocConfig pandocConfig;

    /// 解析 Word 格式的文档
    ///
    /// 使用 pandoc 将 Word 文档转换为 Markdown
    ///
    /// @param document 上传的 Word 文档文件
    /// @return 解析结果，包含解析后的 Markdown 内容
    @Override
    public ConvertDocumentParseResult parse(UploadFile document) {
        var workingDir = fileKit.createTmpDir("word-parse");

        var inputFilepath = Paths.get(workingDir.getAbsolutePath(), "document.docx");

        try (var out = new FileOutputStream(inputFilepath.toFile())) {
            var bytes = document.getInputStream().readAllBytes();
            out.write(bytes);
        } catch (Exception e) {
            throw Errors.INTERNAL_ERROR.toException(e, "保存 Word 文档失败: {}", e.getMessage());
        }

        var executor = new CommandExecutor(log);

        var cmd = buildCommand(inputFilepath);
        String markdown;
        try {
            markdown = executor.exec(cmd, workingDir.getAbsolutePath());
        } catch (Exception e) {
            throw Errors.PARSER_EXECUTION_ERROR.toException(e, "Pandoc 执行失败: {}", e.getMessage());
        } finally {
            FileUtil.del(workingDir);
        }

        var result = new ConvertDocumentParseResult();
        result.setContent(markdown);
        return result;
    }

    /// 构建 pandoc 命令参数列表
    ///
    /// @param inputFilepath 输入 Word 文档路径
    /// @return pandoc 命令参数列表
    private ArrayList<String> buildCommand(Path inputFilepath) {
        var cmd = new ArrayList<String>();
        cmd.add(pandocConfig.getPath());
        cmd.add(inputFilepath.toString());
        cmd.add("--to=markdown");
        cmd.add("--wrap=none");
        cmd.add("--no-highlight");
        return cmd;
    }
}
