package com.mboaops.backend.agents.qwen;

public class QwenClientException extends RuntimeException {

    public QwenClientException(String message) {
        super(message);
    }

    public QwenClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
