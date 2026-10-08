package com.asie.aegisvault.security;

import com.asie.aegisvault.User.*;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AccountPrincipal extends org.springframework.security.core.userdetails.User {
  private final long credentialsVersion;

  public AccountPrincipal(User user) {
    super(
        user.getNickname(),
        user.getPassword(),
        user.getAccountStatus() == AccountStatus.ACTIVE,
        true,
        true,
        user.getAccountStatus() != AccountStatus.LOCKED,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getPosition().name())));
    credentialsVersion = user.getCredentialsVersion();
  }

  public long credentialsVersion() {
    return credentialsVersion;
  }
}
