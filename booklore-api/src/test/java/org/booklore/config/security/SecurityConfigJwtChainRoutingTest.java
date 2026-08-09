package org.booklore.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoints excluded from the JWT API chain never get the JWT filter, so their
 * SecurityContext stays empty and any {@code @PreAuthorize} on them fails with 403.
 * These tests pin which auth paths are public and which are not.
 */
class SecurityConfigJwtChainRoutingTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/register",
            "/api/v1/users",
            "/api/v1/books/1",
            "/komga/api/v1/series"
    })
    @DisplayName("authenticated endpoints are routed through the JWT chain")
    void routesAuthenticatedEndpointsThroughJwtChain(String path) {
        assertThat(SecurityConfig.requiresJwtApiChain(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/remote",
            "/api/v1/auth/logout",
            "/api/v1/auth/oidc/state",
            "/api/v1/auth/oidc/callback",
            "/api/v1/public-settings",
            "/api/v1/setup",
            "/api/v1/setup/status",
            "/api/v1/healthcheck",
            "/api/v1/opds/search.opds",
            "/api/kobo/token/v1/library",
            "/api/docs",
            "/ws/info"
    })
    @DisplayName("public endpoints bypass the JWT chain")
    void bypassesJwtChainForPublicEndpoints(String path) {
        assertThat(SecurityConfig.requiresJwtApiChain(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/authors/1",
            "/api/v1/authors/1/photo/upload"
    })
    @DisplayName("the author endpoints are not confused with the auth endpoints")
    void doesNotTreatAuthorsAsAuth(String path) {
        assertThat(SecurityConfig.requiresJwtApiChain(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/index.html",
            "/assets/logo.png",
            "/"
    })
    @DisplayName("non-API paths are left to the static resource chain")
    void ignoresNonApiPaths(String path) {
        assertThat(SecurityConfig.requiresJwtApiChain(path)).isFalse();
    }
}
