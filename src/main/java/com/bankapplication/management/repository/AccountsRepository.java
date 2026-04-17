package com.bankapplication.management.repository;

import com.bankapplication.management.entity.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountsRepository extends JpaRepository<Accounts, Long> {
    List<Accounts> findByOwner_Id(Long userId);
}

