package com.docconv.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "docconv.deepseek")
public class DeepSeekConfig {

    private String apiUrl = "https://api.deepseek.com/chat/completions";

    private String apiKey;

    private String model = "deepseek-v4-pro";

    private int timeoutSeconds = 120;
}
