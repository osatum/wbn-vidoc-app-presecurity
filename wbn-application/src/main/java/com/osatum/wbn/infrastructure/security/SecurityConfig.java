package com.osatum.wbn.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final IpAccessEnforcementFilter ipAccessEnforcementFilter;
    private final LoginSessionActivityFilter loginSessionActivityFilter;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> {
                    reg.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    reg.requestMatchers(
                                    "/api/auth/**",
                                    "/v3/api-docs",
                                    "/v3/api-docs/**",
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/api/rag/stream",
                                    "/api/home",
                                    "/api/home/**",
                                    "/api/registration/**",
                                    "/api/password-reset/**",
                                    "/api/user-data-deletion/**",
                                    "/api/user-data-deletion/verify/**",
                                    "/api/user-data-deletion/confirm/**",
                                    "/api/migration/import",
                                    "/api/password-reset/request",
                                    "/api/navigation-audit/events/batch"
                            )
                            .permitAll();

                    if (h2ConsoleEnabled) {
                        reg.requestMatchers(PathRequest.toH2Console()).permitAll();
                    }

                    reg.requestMatchers("/api/auth-sessions/**").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/account-activations/**").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/password-reset-requests/**").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/user-data-deletion-requests/**").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/email-messages/**").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/email-templates/**").hasAuthority("ADMIN");
                    reg.requestMatchers(HttpMethod.POST, "/api/navigation-audit/events/search").hasAuthority("ADMIN");
                    reg.requestMatchers(HttpMethod.POST, "/api/navigation-audit/visits/search").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/navigation-audit/reports/**").hasAuthority("ADMIN");
                    reg.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    reg.anyRequest().authenticated();
                })
                .headers(headers -> {
                    if (h2ConsoleEnabled) {
                        headers.frameOptions(frame -> frame.disable());
                    }
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(ipAccessEnforcementFilter, JwtAuthFilter.class)
                .addFilterAfter(loginSessionActivityFilter, IpAccessEnforcementFilter.class);

        return http.build();
    }
}
