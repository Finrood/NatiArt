package com.saas.directory.dto;

import java.util.List;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Response contract of {@code POST /validate-token}: a narrow, versioned
 * snapshot of the validated authentication — never the Spring
 * {@link Authentication} internal, whose serialized shape can shift with
 * framework upgrades and may carry principal internals.
 *
 * <p>The wire shape (principal object + {@code authorities[].authority}) is
 * what product-service's token-validation client deserializes; changing
 * field names here is a cross-service contract break.</p>
 */
public record TokenValidationDto(Object principal, List<AuthorityDto> authorities) {

    public record AuthorityDto(String authority) {}

    public static TokenValidationDto from(Authentication authentication) {
        final List<AuthorityDto> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(AuthorityDto::new)
                .toList();
        return new TokenValidationDto(authentication.getPrincipal(), authorities);
    }
}
