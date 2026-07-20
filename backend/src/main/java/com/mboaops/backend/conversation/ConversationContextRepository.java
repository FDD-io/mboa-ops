package com.mboaops.backend.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationContextRepository extends JpaRepository<ConversationContext, UUID> {

    Optional<ConversationContext> findByClientPhone(String clientPhone);
}
