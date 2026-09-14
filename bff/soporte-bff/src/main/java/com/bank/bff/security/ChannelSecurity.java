package com.bank.bff.security;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class ChannelSecurity {
    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            @Value("${bank.channel}") String channel,
            IdentityRegistry identities, ChannelLogin login) throws Exception {
        if (!Set.of("core", "reports", "web", "mobile", "atm").contains(channel)) {
            throw new IllegalArgumentException("Debe seleccionar un canal valido");
        }

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers.cacheControl(cache -> {}))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                error(response, 401, "Autenticacion requerida"))
                        .accessDeniedHandler((request, response, exception) ->
                                error(response, 403, "Canal no autorizado")))
                .addFilterBefore(tokenFilter(identities, login, channel), UsernamePasswordAuthenticationFilter.class);

        http.authorizeHttpRequests(authorize -> {
            authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            if (channel.equals("core")) {
                authorize.requestMatchers(org.springframework.http.HttpMethod.POST, "/interno/retiros")
                        .hasRole("ATM")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/interno/cuentas/detalle")
                        .hasRole("WEB")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/interno/retiros")
                        .hasRole("WEB")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/interno/cuentas/resumen")
                        .hasRole("MOBILE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/interno/cuentas/saldo")
                        .hasRole("ATM");
            } else if (channel.equals("reports")) {
                authorize.requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/interno/reportes/cuentas/*/movimientos-breves")
                        .hasRole("MOBILE")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/interno/reportes/cuentas/*/movimientos")
                        .hasRole("WEB")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET, "/interno/reportes/**")
                        .hasRole("WEB");
            } else {
                String path = switch (channel) {
                    case "web" -> "/api/web/**";
                    case "mobile" -> "/api/movil/**";
                    default -> "/api/cajero/**";
                };
                String loginPath = switch (channel) {
                    case "web" -> "/api/web/login";
                    case "mobile" -> "/api/movil/login";
                    default -> "/api/cajero/sesion";
                };
                authorize.requestMatchers(org.springframework.http.HttpMethod.POST, loginPath).permitAll();
                authorize.requestMatchers("/api/web/login", "/api/movil/login", "/api/cajero/sesion").denyAll();
                authorize.requestMatchers(path)
                        .hasRole(channel.toUpperCase(Locale.ROOT));
            }
            authorize.anyRequest().denyAll();
        });
        return http.build();
    }

    private OncePerRequestFilter tokenFilter(IdentityRegistry identities, ChannelLogin login, String channel) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    identities.authenticate(header.substring(7)).ifPresent(identity -> {
                            if (channel.equals("atm") && identity.role().equals("ATM")
                                    && identity.terminal() != null
                                    && (!identity.terminal().equals(request.getHeader("X-ATM-Terminal"))
                                    || !login.terminalValid(identity.terminal(), request.getHeader("X-ATM-Key")))) {
                                return;
                            }
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    new BankPrincipal(identity.role(), identity.accountKey()),
                                    null,
                                    List.of(new SimpleGrantedAuthority(
                                            "ROLE_" + identity.role())));
                            SecurityContextHolder.getContext()
                                    .setAuthentication(authentication);
                    });
                }
                chain.doFilter(request, response);
            }
        };
    }

    private static void error(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
