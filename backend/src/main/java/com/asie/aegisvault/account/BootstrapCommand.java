package com.asie.aegisvault.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
public class BootstrapCommand implements ApplicationRunner {
  private final AdministratorBootstrap bootstrap;
  private final ConfigurableApplicationContext context;
  private final String nickname, email, password;

  public BootstrapCommand(
      AdministratorBootstrap bootstrap,
      ConfigurableApplicationContext context,
      @Value("${AEGIS_BOOTSTRAP_NAME:admin}") String nickname,
      @Value("${AEGIS_BOOTSTRAP_EMAIL:}") String email,
      @Value("${AEGIS_BOOTSTRAP_PASSWORD:}") String password) {
    this.bootstrap = bootstrap;
    this.context = context;
    this.nickname = nickname;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    bootstrap.initialize(nickname, email, password);
    org.slf4j.LoggerFactory.getLogger(BootstrapCommand.class)
        .info("최초 관리자 설정을 완료했습니다. 일반 실행으로 서버를 시작해주세요.");
    context.close();
  }
}
