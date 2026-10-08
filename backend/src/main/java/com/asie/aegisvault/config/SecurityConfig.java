package com.asie.aegisvault.config;

import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.CurrentAccountFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type =
        org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      UserRepository userRepository,
      com.asie.aegisvault.audit.SecurityAudit audit)
      throws Exception {
    http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/user/login",
                        "/user/signup",
                        "/account/recovery",
                        "/account/reset",
                        "/error",
                        "/css/**",
                        "/js/**")
                    .permitAll()
                    .requestMatchers("/admin/**", "/dep/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/")
                    .authenticated()
                    .requestMatchers("/document", "/mypage")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .addFilterAfter(
            new CurrentAccountFilter(userRepository), AnonymousAuthenticationFilter.class)
        .formLogin(
            form ->
                form.loginPage("/user/login")
                    .loginProcessingUrl("/user/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          audit.record(
                              authentication.getName(), "LOGIN_SUCCEEDED", request.getRequestURI());
                          response.sendRedirect(request.getContextPath() + "/");
                        })
                    .failureHandler(
                        (request, response, exception) -> {
                          audit.record(
                              request.getParameter("username"),
                              "LOGIN_FAILED",
                              request.getRequestURI());
                          response.sendRedirect(request.getContextPath() + "/user/login?error");
                        })
                    .permitAll())
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    (request, response, exception) -> {
                      audit.record(
                          request.getUserPrincipal() == null
                              ? null
                              : request.getUserPrincipal().getName(),
                          "ACCESS_DENIED",
                          request.getRequestURI());
                      response.sendError(403, "접근 권한이 없습니다.");
                    }))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp ->
                        csp.policyDirectives(
                            "default-src 'self'; style-src 'self'; script-src 'self'; img-src"
                                + " 'self' data:; frame-ancestors 'none'; form-action 'self';"
                                + " base-uri 'self'")));

    return http.build();
  }
}
