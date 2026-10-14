package com.asie.aegisvault.account;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.audit.AuditChain;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.security.SecurityClassification;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("local-test")
@RequiredArgsConstructor
public class LocalTestAccounts implements ApplicationRunner {
  private static final String DEPARTMENT_NAME = "테스트 연구개발부";
  private static final List<TestAccount> ACCOUNTS =
      List.of(
          new TestAccount("test_admin", Position.ADMIN),
          new TestAccount("test_staff", Position.STAFF),
          new TestAccount("test_assistant", Position.ASSISTANT_MANAGER),
          new TestAccount("test_manager", Position.MANAGER),
          new TestAccount("test_deputy", Position.DEPUTY_GENERAL_MANAGER),
          new TestAccount("test_general", Position.GENERAL_MANAGER),
          new TestAccount("test_executive", Position.EXECUTIVE));

  private final UserRepository users;
  private final DepartmentRepository departments;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;
  private final PlatformTransactionManager transactionManager;
  private final AuditChain audit;
  private final Environment environment;

  @Override
  public void run(ApplicationArguments arguments) {
    requireLocalTestStorage();
    String password = environment.getRequiredProperty("app.test-accounts.password");
    PasswordPolicy.validate(password);
    if (!audit.verify().valid()) {
      throw new IllegalStateException("테스트 DB의 기존 감사 기록을 검증할 수 없습니다.");
    }
    Integer created =
        new TransactionTemplate(transactionManager).execute(status -> createAccounts(password));
    if (!audit.verify().valid()) {
      throw new IllegalStateException("테스트 계정 생성 후 감사 기록 검증에 실패했습니다.");
    }
    LoggerFactory.getLogger(LocalTestAccounts.class)
        .info("Local test accounts ready: {} created, existing accounts preserved", created);
  }

  private int createAccounts(String password) {
    Department department =
        departments.findAll().stream()
            .filter(candidate -> DEPARTMENT_NAME.equals(candidate.getName()))
            .findFirst()
            .orElse(null);
    boolean newDepartment = department == null;
    if (newDepartment) {
      department =
          departments.saveAndFlush(new Department(DEPARTMENT_NAME, "직급별 문서 작성·검토 테스트용 부서"));
    } else if (department.isClosed()) {
      throw new IllegalStateException("테스트 연구개발부가 폐쇄되어 있습니다.");
    }
    User admin = null;
    int created = 0;
    for (TestAccount spec : ACCOUNTS) {
      User account = users.findByNickname(spec.nickname()).orElse(null);
      String email = spec.nickname() + "@aegisvault.test";
      if (account != null) {
        if (!email.equals(account.getEmail())) {
          throw new IllegalStateException("기존 계정과 테스트 아이디가 겹칩니다: " + spec.nickname());
        }
        if (spec.position() == Position.ADMIN) {
          admin = account;
        }
        continue;
      }
      if (users.existsByEmail(email)) {
        throw new IllegalStateException("테스트 이메일이 이미 사용 중입니다: " + email);
      }
      account = new User(spec.nickname(), email, passwords.encode(password));
      account.assign(spec.position(), department);
      if (spec.position() == Position.ADMIN) {
        account.changeClearance(SecurityClassification.RESTRICTED);
      }
      users.saveAndFlush(account);
      if (spec.position() == Position.ADMIN) {
        admin = account;
      }
      events.publishEvent(
          AuditEvent.of(
              admin,
              "TEST_ACCOUNT_CREATED",
              "USER",
              account.getId(),
              null,
              "로컬 테스트 계정 생성: " + spec.nickname() + " / " + spec.position().getDisplayName()));
      created++;
    }
    if (newDepartment) {
      events.publishEvent(
          AuditEvent.of(
              admin,
              "DEPARTMENT_CREATED",
              "DEPARTMENT",
              department.getId(),
              null,
              "로컬 테스트 부서 생성: " + DEPARTMENT_NAME));
    }
    return created;
  }

  private void requireLocalTestStorage() {
    String url = environment.getRequiredProperty("spring.datasource.url");
    String prefix = "jdbc:h2:file:";
    if (!url.startsWith(prefix)) {
      throw new IllegalStateException("테스트 계정은 local/test DB에서만 생성할 수 있습니다.");
    }
    Path database = Path.of(url.substring(prefix.length()).split(";", 2)[0]).toAbsolutePath().normalize();
    Path expectedDatabase = Path.of("local/test/aegisvault").toAbsolutePath().normalize();
    Path key = Path.of(environment.getRequiredProperty("app.audit.key-file")).toAbsolutePath().normalize();
    Path expectedKey = Path.of("local/test/audit.key").toAbsolutePath().normalize();
    if (!database.equals(expectedDatabase) || !key.equals(expectedKey)) {
      throw new IllegalStateException("local-test 프로필의 DB와 감사 키 경로를 확인해주세요.");
    }
  }

  private record TestAccount(String nickname, Position position) {}
}
