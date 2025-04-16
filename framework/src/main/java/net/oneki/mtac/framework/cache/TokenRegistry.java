package net.oneki.mtac.framework.cache;

import java.util.HashMap;

import org.springframework.stereotype.Component;

import net.oneki.mtac.model.core.util.security.Claims;

@Component
public class TokenRegistry extends HashMap<String, Claims> { // key=jti

}
