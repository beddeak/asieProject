package com.asie.aegisvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.CurrentAccountFilter;


@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http.authorizeHttpRequests(requests ->
            requests.requestMatchers("/user/login", "/user/signup", "/css/**", "/js/**").permitAll()
            .requestMatchers("/admin/**", "/dep/**").hasRole("ADMIN")
            .requestMatchers("/").authenticated()
            .requestMatchers("/document","/mypage").authenticated()
            .anyRequest().authenticated()
        )
            .addFilterAfter(new CurrentAccountFilter(userRepository), AnonymousAuthenticationFilter.class)
            .formLogin(form -> form.loginPage("/user/login").loginProcessingUrl("/user/login")
                    .defaultSuccessUrl("/", true).permitAll());
        return http.build();
    }
    @Bean 
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
