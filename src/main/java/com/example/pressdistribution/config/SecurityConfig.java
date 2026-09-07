package com.example.pressdistribution.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({BootstrapAdminProperties.class, SeedProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/recovery", "/login/recovery/success", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/admin/parishes", "/admin/parishes/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/admin/users", "/admin/users/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/admin/publications", "/admin/publications/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/admin/issues", "/admin/issues/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/issues/new").hasAnyAuthority("ROLE_ADMINISTRATOR", "ROLE_PARISH_PRIEST")
                .requestMatchers("/issues").hasAnyAuthority("ROLE_ADMINISTRATOR", "ROLE_PARISH_PRIEST")
                .requestMatchers("/issues/defaults").hasAnyAuthority("ROLE_ADMINISTRATOR", "ROLE_PARISH_PRIEST")
                .requestMatchers("/admin/records/bulk", "/admin/records/bulk/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/records", "/records/**").hasAnyAuthority("ROLE_ADMINISTRATOR", "ROLE_PARISH_PRIEST")
                .requestMatchers("/reports/publications", "/reports/publications/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/reports/parishes", "/reports/parishes/**").hasAuthority("ROLE_ADMINISTRATOR")
                .requestMatchers("/reports/my-parish", "/reports/my-parish/**").hasAuthority("ROLE_PARISH_PRIEST")
                .requestMatchers("/profile", "/profile/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl("/login?logout")
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
