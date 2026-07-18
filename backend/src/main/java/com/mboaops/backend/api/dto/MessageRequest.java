package com.mboaops.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MessageRequest {

    @NotBlank
    private String clientPhone;

    @NotNull
    private MessageType type;

    @NotBlank
    private String content;

    public MessageRequest() {
    }

    public MessageRequest(String clientPhone, MessageType type, String content) {
        this.clientPhone = clientPhone;
        this.type = type;
        this.content = content;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
