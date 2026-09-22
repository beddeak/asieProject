package com.asie.aegisvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests ->
            requests.requestMatchers("/user/login","/","/user/signup").permitAll()
            .requestMatchers("/document","/mypage").authenticated()
            .anyRequest().authenticated()
        )
            .formLogin(from -> from.loginPage("/user/login").loginProcessingUrl("/user/login").permitAll());
        return http.build();
    }
    @Bean 
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
