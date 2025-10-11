package apu.saerok_admin.config;

import apu.saerok_admin.security.LoginSessionAuthenticationFilter;
import apu.saerok_admin.security.LoginSessionManager;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LoginSessionAuthenticationFilter loginSessionAuthenticationFilter,
            LoginSessionManager loginSessionManager
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        // ▼ 프록시 엔드포인트 공개
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/login", "/auth/callback/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .addLogoutHandler((request, response, authentication) -> {
                            loginSessionManager.clearSession(request);
                            loginSessionManager.deleteRefreshCookie(response, request.isSecure());
                        })
                );
        http.addFilterAfter(loginSessionAuthenticationFilter, SecurityContextPersistenceFilter.class);
        return http.build();
    }

    @Bean
    public LoginSessionAuthenticationFilter loginSessionAuthenticationFilter(LoginSessionManager loginSessionManager) {
        return new LoginSessionAuthenticationFilter(loginSessionManager);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
