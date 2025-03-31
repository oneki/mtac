package net.oneki.mtac.model.core.util.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JwtAuthoritiesExtractor
 */
public interface JwtAuthoritiesExtractor {

    public Collection<GrantedAuthority> extratAuthorities(Jwt token);
}