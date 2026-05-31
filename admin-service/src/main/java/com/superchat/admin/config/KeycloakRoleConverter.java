package com.superchat.admin.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak realm roles (realm_access.roles) to Spring Security ROLE_ authorities.
 * Example: "org_admin" → ROLE_ORG_ADMIN so @PreAuthorize("hasRole('ORG_ADMIN')") works.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return Collections.emptyList();
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List<?> roles)) return Collections.emptyList();
        return roles.stream()
                .filter(r -> r instanceof String)
                .map(r -> (String) r)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .map(a -> (GrantedAuthority) a)
                .toList();
    }
}
