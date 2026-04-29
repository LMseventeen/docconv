package com.docconv.converter.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "docconv.storage")
public class StorageConfig {

    private String tempDir = System.getProperty("java.io.tmpdir") + "/docconv";

    public Path getTempDirPath() {
        return Path.of(tempDir);
    }

    @PostConstruct
    public void ensureTempDir() throws IOException {
        Files.createDirectories(getTempDirPath());
    }
}
