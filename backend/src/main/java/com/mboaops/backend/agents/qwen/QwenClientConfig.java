package com.mboaops.backend.agents.qwen;

import com.mboaops.backend.config.QwenProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class QwenClientConfig {

    /** Les payloads multimodaux (audio/image en base64) dépassent largement
     *  la limite par défaut de 256 KB des codecs WebClient. */
    private static final int MAX_IN_MEMORY_SIZE = 32 * 1024 * 1024;

    @Bean
    public WebClient qwenWebClient(QwenProperties qwenProperties) {
        return WebClient.builder()
                .baseUrl(qwenProperties.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + qwenProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
