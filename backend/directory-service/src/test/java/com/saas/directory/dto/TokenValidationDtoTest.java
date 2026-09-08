package com.saas.directory.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Locks the {@code POST /validate-token} wire contract: principal + authority
 * names only, no Spring {@code Authentication} internals. product-service's
 * token-validation client deserializes exactly this shape.
 */
class TokenValidationDtoTest {

    @Test
    void from_mapsPrincipalAndAuthorityNamesOnly() {
        final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                Map.of("username", "alice"), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        final TokenValidationDto dto = TokenValidationDto.from(authentication);

        assertEquals(Map.of("username", "alice"), dto.principal());
        assertEquals(1, dto.authorities().size());
        assertEquals("ROLE_USER", dto.authorities().get(0).authority());
    }

    @Test
    void serialization_carriesOnlyTheContractFields() throws Exception {
        final TokenValidationDto dto = new TokenValidationDto(
                Map.of("username", "alice"), List.of(new TokenValidationDto.AuthorityDto("ROLE_USER")));

        final String json = new ObjectMapper().writeValueAsString(dto);
        final JsonNode node = new ObjectMapper().readTree(json);

        assertEquals("alice", node.get("principal").get("username").asText());
        assertEquals(
                "ROLE_USER", node.get("authorities").get(0).get("authority").asText());
        // framework internals of the old Authentication payload must stay out of the contract
        assertFalse(node.has("details"));
        assertFalse(node.has("authenticated"));
        assertFalse(node.has("name"));
        assertFalse(node.has("credentials"));
    }
}
