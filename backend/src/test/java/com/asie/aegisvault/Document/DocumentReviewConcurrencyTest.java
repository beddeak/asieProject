package com.asie.aegisvault.Document;

import static org.junit.jupiter.api.Assertions.*;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.security.UserAccessPolicy;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
    showSql = false,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:aegisvault-review-concurrency;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.open-in-view=false",
      "logging.file.name="
    })
@Import({
  DocumentService.class,
  UserAccessPolicy.class,
  com.asie.aegisvault.security.DocumentAccess.class,
  DocumentLocks.class,
  com.asie.aegisvault.project.ProjectAccess.class
})
class DocumentReviewConcurrencyTest {
  @Autowired private EntityManager entityManager;
  @Autowired private DocumentService service;
  @Autowired private DocumentVersionRepository versions;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousApprovalAndRejectionProduceExactlyOneDecision() throws Exception {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    Long[] ids =
        transaction.execute(
            status -> {
              Department department = new Department("동시 검토 테스트", "메모리 DB");
              entityManager.persist(department);
              User author = user("author", Position.STAFF, department);
              User approver = user("approver", Position.MANAGER, department);
              User rejecter = user("rejecter", Position.MANAGER, department);
              Document document = service.create("동시 검토", "본문", Position.STAFF, author.getId());
              Long versionId =
                  versions
                      .findFirstByDocumentOrderByVersionNumberDesc(document)
                      .orElseThrow()
                      .getId();
              return new Long[] {versionId, approver.getId(), rejecter.getId()};
            });
    assertNotNull(ids);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var approval =
          executor.submit(() -> decide(ready, start, () -> service.approve(ids[0], ids[1])));
      var rejection =
          executor.submit(
              () -> decide(ready, start, () -> service.reject(ids[0], ids[2], "시험 근거 보완 필요")));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      int approvalResult = approval.get(15, TimeUnit.SECONDS);
      int rejectionResult = rejection.get(15, TimeUnit.SECONDS);
      assertEquals(
          List.of(200, 409), List.of(approvalResult, rejectionResult).stream().sorted().toList());
      transaction.executeWithoutResult(
          status -> {
            DocumentVersion result = versions.findById(ids[0]).orElseThrow();
            assertEquals(
                approvalResult == 200 ? DocumentStatus.APPROVED : DocumentStatus.REJECTED,
                result.getStatus());
            assertEquals(approvalResult == 200 ? ids[1] : ids[2], result.getReviewedBy().getId());
            assertNotNull(result.getReviewedAt());
          });
    } finally {
      start.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private int decide(CountDownLatch ready, CountDownLatch start, Runnable operation)
      throws InterruptedException {
    ready.countDown();
    if (!start.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("검토 테스트 시작 시간이 초과되었습니다.");
    }
    try {
      operation.run();
      return 200;
    } catch (ResponseStatusException exception) {
      return exception.getStatusCode().value();
    }
  }

  private User user(String nickname, Position position, Department department) {
    User user = new User(nickname, nickname + "@example.com", "hash");
    ReflectionTestUtils.setField(user, "position", position);
    ReflectionTestUtils.setField(user, "department", department);
    entityManager.persist(user);
    return user;
  }
}
