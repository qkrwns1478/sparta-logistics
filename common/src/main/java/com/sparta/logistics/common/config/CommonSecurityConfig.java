package com.sparta.logistics.common.config;

import com.sparta.logistics.common.filter.GatewayAuthFilter;
import com.sparta.logistics.common.security.GatewayAuthEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 나중에 각 서비스에서 @PreAuthorize를 쓰기 위해 공통으로 켜둡니다!
@RequiredArgsConstructor
@Slf4j
public class CommonSecurityConfig {

    private final GatewayAuthEntryPoint gatewayAuthEntryPoint;
    private final GatewayAuthFilter gatewayAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 비활성화 (JWT 사용하므로 불필요)
                .csrf(csrf -> csrf.disable())
                // 2. 기본 로그인 폼 및 HTTP Basic 인증 비활성화
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // 3. 세션을 사용하지 않음 (Stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 4. 공통 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // Swagger 및 Actuator는 인증 없이 통과
                        .requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/refresh"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // 5. 인증 실패 시 GatewayAuthEntryPoint로 처리
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(gatewayAuthEntryPoint)
                )
                // GatewayAuthFilter를 AuthorizationFilter 이전에 등록
                // → SecurityContext에 인증 정보를 먼저 세팅한 뒤 권한 체크가 이루어짐
                .addFilterBefore(gatewayAuthFilter, AuthorizationFilter.class)
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        log.info(">>> SecurityContextHolderFilter 직전: {}",
                                SecurityContextHolder.getContext().getAuthentication());
                        chain.doFilter(req, res);
                        log.info(">>> SecurityContextHolderFilter 직후: {}",
                                SecurityContextHolder.getContext().getAuthentication());
                    }
                }, SecurityContextHolderFilter.class);
        return http.build();
    }

    // GatewayAuthFilter는 Security FilterChain에만 등록되어야 함.
    // @Component에 의해 서블릿 필터로도 자동 등록되는 것을 방지함.
    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(GatewayAuthFilter filter) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}