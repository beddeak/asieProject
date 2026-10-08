package com.asie.aegisvault.account;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery, Long> {
  Optional<PasswordRecovery> findByTokenHash(String hash);

  List<PasswordRecovery> findByUserIdAndCompletedAtIsNull(Long userId);

  Page<PasswordRecovery> findByCompletedAtIsNull(Pageable page);
}
