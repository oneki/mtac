package net.oneki.mtac.model.core.util.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.Data;

/**
 * DefaultOauth2User
 */
@Data
public class DefaultOAuth2User implements OAuth2User {
	private Map<String, Object> context = new HashMap<>();
	private Map<String, Object> logContext = new HashMap<>();
    private Map<String, Object> attributes;
    private Collection<? extends GrantedAuthority> authorities;
	private String nameAttributeKey;
	private String token;
	private boolean tokenRefreshed = false;

    public DefaultOAuth2User(Collection<? extends GrantedAuthority> authorities, Map<String, Object> attributes, String nameAttributeKey, String token, boolean tokenRefreshed) {
		if (!attributes.containsKey(nameAttributeKey)) {
			throw new IllegalArgumentException("Missing attribute '" + nameAttributeKey + "' in attributes");
		}
		this.authorities = Collections.unmodifiableSet(new LinkedHashSet<>(this.sortAuthorities(authorities)));
		this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
		this.nameAttributeKey = nameAttributeKey;
		this.token = token;
		this.tokenRefreshed = tokenRefreshed;
		// this.setContext(ThreadLocalContext.getContext() != null ? ThreadLocalContext.getContext() : new HashMap<>());
	}

	@Override
	public String getName() {
		return Optional.ofNullable(this.getAttribute(this.nameAttributeKey)).map(Object::toString).orElse(null);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return this.authorities;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return this.attributes;
	}

	public String getToken() {
		return token;
	}

    private Set<GrantedAuthority> sortAuthorities(Collection<? extends GrantedAuthority> authorities) {
		SortedSet<GrantedAuthority> sortedAuthorities =
				new TreeSet<>(Comparator.comparing(GrantedAuthority::getAuthority));
		sortedAuthorities.addAll(authorities);
		return sortedAuthorities;
	}
}