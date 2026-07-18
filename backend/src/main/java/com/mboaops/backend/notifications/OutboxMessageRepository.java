package com.mboaops.backend.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByStatutAndProchaineTentativeLeBefore(OutboxStatut statut, Instant instant);
}
