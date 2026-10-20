package com.nexus.quiz.security;

import com.nexus.quiz.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    public JwtFilter(
            JwtService jwtService,
            UserDetailsServiceImpl userDetailsService,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        return LOGIN_PATH.equals(path) || REFRESH_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        Optional<JwtService.AccessTokenClaims> parsedClaims = jwtService.parseAccessToken(token);
        if (parsedClaims.isEmpty()) {
            rejectAuthentication(request, response, new BadCredentialsException("Invalid or expired access token"));
            return;
        }

        try {
            JwtService.AccessTokenClaims claims = parsedClaims.get();
            User user = userDetailsService.loadActiveUserById(claims.userId());
            if (user.getRole() != claims.role()) {
                rejectAuthentication(request, response, new BadCredentialsException("Invalid access token"));
                return;
            }

            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                            user.getId(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (org.springframework.security.core.AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            rejectAuthentication(request, response, exception);
        } catch (AccessDeniedException exception) {
            SecurityContextHolder.clearContext();
            accessDeniedHandler.handle(request, response, exception);
        }
    }

    private void rejectAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.core.AuthenticationException exception) throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, exception);
    }
}
