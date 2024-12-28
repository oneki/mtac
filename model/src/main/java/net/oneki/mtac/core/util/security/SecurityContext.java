package net.oneki.mtac.core.util.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.Data;

@Data
public class SecurityContext {
    public String getUsername() {
        if (SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            String username = "";
            if (principal instanceof OAuth2User) {
                return ((OAuth2User) principal).getName();
            }
            if (principal instanceof UserDetails) {
                username = ((UserDetails) principal).getUsername();
            } else {
                username = principal.toString();
            }

            return username;
        } else {
            return null;
        }
    }

    public Map<String, Object> getAttributes() {
        Map<String, Object> result = new HashMap<>();
        Object principal = getPrincipal();
        if (principal == null || !(principal instanceof OAuth2User)) {
            return result;
        }
        return ((OAuth2User) principal).getAttributes();
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        Map<String, Object> attributes = getAttributes();
        return (T) attributes.get(key);
    }
    
    public String getSubject() {
        return getAttribute("sub");
    }

    public String getAudience() {
        return getAttribute("aud");
    }

    public String getIssuer() {
        return getAttribute("iss");
    }

    public List<Integer> getSids() {
        return getAttribute("sids");
    }

    public List<Integer> getTenantSids() {
        return getAttribute("tenantSids");
    }

    public UserDetails getUserDetails() {
        if (SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            UserDetails userDetails = null;

            if (principal instanceof UserDetails) {
                userDetails = (UserDetails) principal;
            }

            return userDetails;
        } else {
            return null;
        }

    }

    public String getJwtToken() {
        if (SecurityContextHolder.getContext() != null
        && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            if (principal instanceof DefaultOAuth2User) {
                return ((DefaultOAuth2User) principal).getToken();
            }
        }

        return null;       
     }

    private Object getPrincipal() {
        if (SecurityContextHolder.getContext() != null
            && SecurityContextHolder.getContext().getAuthentication() != null) {
            return SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }
        return null;
    }

    public Object getContext(String key) {
        initNonAuthenticatedContext();
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof DefaultOAuth2User) {
                return ((DefaultOAuth2User) principal).getContext().get(key);
            }  
        }
        return null;
    }

    public Map<String, Object> getLogContext() {
        initNonAuthenticatedContext();
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof DefaultOAuth2User) {
                return ((DefaultOAuth2User) principal).getLogContext();
            }  
        }
        return null;
    }

    public Map<String, Object> getContext() {
        initNonAuthenticatedContext();
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof DefaultOAuth2User) {
                return ((DefaultOAuth2User) principal).getContext();
            }  
        }
        return null;
    }

    public void addContext(String key, Object value, boolean appearsInLog) {
        initNonAuthenticatedContext();
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof DefaultOAuth2User) {
                ((DefaultOAuth2User) principal).getContext().put(key, value);
                if (appearsInLog) {
                    if (value == null) value = "";
                    ((DefaultOAuth2User) principal).getLogContext().put(key, value.toString());
                }
            }  
        }
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            return SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        }
        return null;       
    }

    private void initNonAuthenticatedContext() {
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "Not Authenticated");
            OAuth2User principal = new DefaultOAuth2User(new ArrayList<>(), attributes, "sub", null);
            Authentication authentication = new OAuth2AuthenticationToken(principal, new ArrayList<>(), "default");
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}