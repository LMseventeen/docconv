package com.docconv.converter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "docconv.pandoc")
public class PandocConfig {

    private String path = "pandoc";
    private int timeoutSeconds = 30;
}
