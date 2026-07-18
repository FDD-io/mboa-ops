package com.mboaops.backend.agents.qwen;

import java.util.List;

public record QwenChatRequest(String model, List<QwenChatMessage> messages, Double temperature) {
}
