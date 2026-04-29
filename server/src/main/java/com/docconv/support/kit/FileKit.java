package com.docconv.converter.support.kit;

import com.docconv.converter.config.StorageConfig;
import com.docconv.converter.support.exception.Errors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileKit {

    private final StorageConfig storageConfig;

    /// 在临时目录基路径下创建带有给定前缀的临时目录，并返回其绝对路径。
    ///
    /// @param prefix 临时目录前缀
    /// @return 新创建临时目录的绝对路径
    public File createTmpDir(String prefix) {
        var tmpBaseDir = storageConfig.getTempDir();
        if (tmpBaseDir == null || tmpBaseDir.isBlank()) {
            throw Errors.INTERNAL_ERROR.toException("创建临时目录失败：未配置系统临时目录");
        }

        prefix = (prefix == null || prefix.isBlank()) ? "" : prefix + "-";
        var tmpDirPath = Paths.get(tmpBaseDir, prefix + UUID.randomUUID());
        if (Files.exists(tmpDirPath)) {
            throw Errors.INTERNAL_ERROR.toException("创建临时目录失败：临时目录已存在");
        }

        try {
            Files.createDirectories(tmpDirPath);
            return tmpDirPath.toFile();
        } catch (Exception e) {
            throw Errors.INTERNAL_ERROR.toException(e, "创建临时目录失败: {}", e.getMessage());
        }
    }
}
