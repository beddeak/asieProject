package com.asie.aegisvault.security;

import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 로그인 시점의 권한 대신 현재 DB 상태를 적용하여 밴·삭제·직급 변경을 즉시 반영합니다. */
@RequiredArgsConstructor
public class CurrentAccountFilter extends OncePerRequestFilter {
  private final UserRepository userRepository;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getServletPath().startsWith("/css/")
        || request.getServletPath().startsWith("/js/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      User user = userRepository.findByNickname(authentication.getName()).orElse(null);
      if (user == null
          || user.getAccountStatus() != AccountStatus.ACTIVE
          || user.getPosition() == null
          || authentication.getPrincipal() instanceof AccountPrincipal account
              && account.credentialsVersion() != user.getCredentialsVersion()) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
          request.getSession(false).invalidate();
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "이용할 수 없는 계정입니다. 다시 로그인해주세요.");
        return;
      }
      var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getPosition().name()));
      var refreshed =
          UsernamePasswordAuthenticationToken.authenticated(
              authentication.getPrincipal(), authentication.getCredentials(), authorities);
      refreshed.setDetails(authentication.getDetails());
      SecurityContextHolder.getContext().setAuthentication(refreshed);
    }
    chain.doFilter(request, response);
  }
}
