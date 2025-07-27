package com.example.reserve.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    Optional<Outbox> findByUserId(String userId);

}
