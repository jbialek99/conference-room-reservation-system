package org.example.letstry.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(name = "booking.mode", havingValue = "demo")
public class DemoSecurityConfig {

    @Bean
    public PasswordEncoder demoPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService demoUserDetailsService(PasswordEncoder demoPasswordEncoder, @org.springframework.beans.factory.annotation.Value("${demo.password}") String demoPassword) {
        String password = demoPasswordEncoder.encode(demoPassword);
        return new InMemoryUserDetailsManager(
                User.withUsername("demo1").password(password).roles("DEMO").build(),
                User.withUsername("demo2").password(password).roles("DEMO").build()
        );
    }

    @Bean
    public SecurityFilterChain demoSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.defaultSuccessUrl("/demo", true).permitAll())
                .logout(logout -> logout
                        .logoutUrl("/demo/logout")
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());
        return http.build();
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("https://graph.microsoft.com/v1.0")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }
}