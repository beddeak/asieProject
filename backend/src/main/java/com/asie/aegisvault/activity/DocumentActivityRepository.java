package com.asie.aegisvault.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentActivityRepository extends JpaRepository<DocumentActivity, Long>,
        JpaSpecificationExecutor<DocumentActivity> {
}
