package net.oneki.mtac.util.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import net.oneki.mtac.util.security.JwtAuthoritiesExtractor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JwtAuthoritiesExtractor
 * Be default, this extractor works with any Open ID server: 
 * It expects a list of keys that represent roles in the token (i.e: roles, scope, cognito:groups, ...)
 * For each key:
 * - if the value is an array, it converts each entry to a grantedAuthority
 * - if the value is a string, it splits the string and converts each token to a grantedAuthority
 * 
 * Example of JWT claims:
 * {
 *   "roles": ["admin", "developer"],
 *   "scope": "service/common service/party",
 *   "cognito:groups": ["analyst", "claim_manager"]
 * }
 * The grantedAuthorities will be the following:
 * - admin
 * - developer
 * - service/common
 * - service/party
 * - analyst
 * - claim_manager
 */
public class DefaultJwtAuthoritiesExtractor implements JwtAuthoritiesExtractor {

    private AuthorityKey[] authoritiesKeys;

    public DefaultJwtAuthoritiesExtractor(AuthorityKey... authoritiesKeys) {
        this.authoritiesKeys = authoritiesKeys;
    }

    @Override
    public Collection<GrantedAuthority> extratAuthorities(Jwt token) {
        // The roles are stored in the attribute "roles" in the JWT token
        Collection<String> roles = new ArrayList<>();
        for (AuthorityKey key : authoritiesKeys) {
            handleClaimEntry(key, token, roles);
        }

        // @formatter:off
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority(role))
            .collect(Collectors.toList());
        // @formatter:on
    }

    private void handleClaimEntry(AuthorityKey authorityKey, Jwt token, Collection<String> authorities) {
        if (authorityKey.isArray()) {
            Collection<String> rolesByKey = token.getClaim(authorityKey.getKey());
            if (rolesByKey != null) {
                authorities.addAll(token.getClaim(authorityKey.getKey()));
            }
        } else {
            String rolesByKey = token.getClaim(authorityKey.getKey());
            if (rolesByKey != null) {
                String[] roles = rolesByKey.split(authorityKey.getSeparator());
                authorities.addAll(Arrays.asList(roles));
            }
            
        }   
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorityKey {
        private String key;
        private boolean array = false;
        private String separator = " ";

        public AuthorityKey(String key) {
            this.key = key;
        }

        public AuthorityKey(String key, boolean isArray) {
            this.key = key;
            this.array = isArray;
        }
    }
}