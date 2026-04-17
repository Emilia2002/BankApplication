package com.bankapplication.management.repository;

import com.bankapplication.management.entity.Transfers;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransfersRepository extends JpaRepository<Transfers, Long> {
    List<Transfers> findByOwner_Id(Long userId);
}
