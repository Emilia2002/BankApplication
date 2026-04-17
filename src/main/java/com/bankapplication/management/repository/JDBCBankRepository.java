package com.bankapplication.management.repository;

import com.bankapplication.management.entity.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JDBCBankRepository extends JpaRepository<Accounts, Long> {
    boolean existsByAccountNumber(String accountNumber);

    Accounts findByAccountNumber(String accountNumber);
}
