package com.bankapplication.management.repository;

import com.bankapplication.management.entity.Cards;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardsRepository extends JpaRepository<Cards, Long> {
    List<Cards> findByAccountOwnerId(Long userId);
    List<Cards> findByAccountId(Long accountId);
}
