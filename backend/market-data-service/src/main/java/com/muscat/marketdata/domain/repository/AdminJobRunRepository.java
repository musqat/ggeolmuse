package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.AdminJobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminJobRunRepository extends JpaRepository<AdminJobRun, String> {
}
