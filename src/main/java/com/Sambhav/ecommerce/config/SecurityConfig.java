
package com.Sambhav.ecommerce.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.Sambhav.ecommerce.filter.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth

                // Public authentication endpoints
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/error").permitAll()

                // Allow anyone to browse products and categories
                .requestMatchers(
                    HttpMethod.GET,
                    "/products", "/products/**",
                    "/categories", "/categories/**"
                ).permitAll()

                // Allow browser CORS preflight requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Only admins can create products or categories
                .requestMatchers(
                    HttpMethod.POST,
                    "/products", "/products/**",
                    "/categories", "/categories/**"
                ).hasRole("ADMIN")

                // Only admins can update products or categories
                .requestMatchers(
                    HttpMethod.PUT,
                    "/products", "/products/**",
                    "/categories", "/categories/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.PATCH,
                    "/products", "/products/**",
                    "/categories", "/categories/**"
                ).hasRole("ADMIN")

                // Only admins can view all customers' orders.
                .requestMatchers(HttpMethod.GET, "/admin/orders").hasRole("ADMIN")

                // Only admins can update order status
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/orders/*/status"
                ).hasRole("ADMIN")

                // Only admins can delete products or categories
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/products", "/products/**",
                    "/categories", "/categories/**"
                ).hasRole("ADMIN")

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())

            .exceptionHandling(ex ->
                ex.authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                )
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
            new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }
}
