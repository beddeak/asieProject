package com.asie.aegisvault.audit;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class SecurityAudit {
  private final AuditChain chain;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String actor, String action, String path) {
    String name = actor == null ? "anonymous" : actor.replaceAll("[\\p{Cntrl}]", "_");
    if (name.length() > 100) name = name.substring(0, 100);
    String route = path == null ? "" : path.replaceAll("[\\p{Cntrl}]", "_");
    if (route.length() > 1000) route = route.substring(0, 1000);
    chain.append(new AuditEvent(name, action, "SECURITY", null, null, route, List.of(), null));
  }
}
