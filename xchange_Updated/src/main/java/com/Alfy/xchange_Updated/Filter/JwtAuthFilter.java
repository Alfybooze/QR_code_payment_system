package com.Alfy.xchange_Updated.Filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.Alfy.xchange_Updated.Services.JwtService;
import java.io.IOException;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            HttpSession session = request.getSession(false);
            String token = null;
            String username = null;
            boolean isSessionInvalid = false;
    
            // Check if session exists and is valid
            if (session == null || session.isNew()) {
                isSessionInvalid = true;
            } else {
                token = (String) session.getAttribute("jwt_token");
                // Check if token exists and is valid
                if (token != null) {
                    try {
                        if (!jwtService.isTokenValid(token)) {
                            isSessionInvalid = true;
                            session.removeAttribute("jwt_token");
                        } else {
                            username = jwtService.extractUsername(token);
                        }
                    } catch (Exception e) {
                        log.error("Invalid JWT token: {}", e.getMessage());
                        isSessionInvalid = true;
                        session.removeAttribute("jwt_token");
                    }
                } else {
                    isSessionInvalid = true;
                }
            }
    
            // If session is invalid and it's not an allowed path, redirect to login
            if (isSessionInvalid && !shouldNotFilter(request)) {
                log.info("Session expired or invalid, redirecting to login");
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-Session-Expired", "true");
                response.sendRedirect("/login");
                return;
            }
    
            // Continue with normal authentication flow
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
    
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            response.sendRedirect("/login");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || 
               path.startsWith("/js/") || 
               path.startsWith("/images/") ||
               path.startsWith("/videos/") ||
               path.equals("/login") ||
               path.equals("/register") ||
               path.equals("/") ||
               path.equals("/forgot_pass") ||
               path.equals("/user/logins") ||
               path.equals("/user/registers") ||
               path.equals("/user/forgot-password") ||
               path.equals("/user/update_bal/{id}") ||
               path.equals("/user/send-code") ||
               path.equals("/user/reset-password") ||
               path.equals("/reset-password") ||
               path.contains("favicon.ico");
    }
}