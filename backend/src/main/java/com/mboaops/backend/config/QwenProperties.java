package com.mboaops.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du client LLM Qwen (API compatible OpenAI), alimentée par
 * les variables d'environnement QWEN_API_KEY et QWEN_BASE_URL.
 */
@ConfigurationProperties(prefix = "mboa-ops.qwen")
public class QwenProperties {

    private String apiKey;
    private String baseUrl;
    private String modelFast;
    private String modelReasoning;
    private String modelVl;
    private String modelAsr;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelFast() {
        return modelFast;
    }

    public void setModelFast(String modelFast) {
        this.modelFast = modelFast;
    }

    public String getModelReasoning() {
        return modelReasoning;
    }

    public void setModelReasoning(String modelReasoning) {
        this.modelReasoning = modelReasoning;
    }

    public String getModelVl() {
        return modelVl;
    }

    public void setModelVl(String modelVl) {
        this.modelVl = modelVl;
    }

    public String getModelAsr() {
        return modelAsr;
    }

    public void setModelAsr(String modelAsr) {
        this.modelAsr = modelAsr;
    }
}
