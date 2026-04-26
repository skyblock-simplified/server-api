package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;

/**
 * Hierarchical access roles assignable to an API key.
 *
 * <p>Declaration order defines the hierarchy - earlier entries inherit all permissions
 * of later entries. The hierarchy is materialized as a Spring Security
 * {@link org.springframework.security.access.hierarchicalroles.RoleHierarchy} bean in
 * {@link ApiKeySecurityConfig}, derived from this enum's declaration order so adding a
 * constant requires no other changes.</p>
 */
public enum ApiKeyRole {

    DEVELOPER,
    SUPER_ADMIN,
    ADMIN,
    SUPER_MODERATOR,
    MODERATOR,
    SUPER_USER,
    USER,
    LIMITED_ACCESS;

    /**
     * The Spring Security authority string for this role, prefixed with {@code ROLE_}.
     *
     * @return the authority string consumed by {@code hasRole(...)} and
     *         {@code RoleHierarchyImpl}
     */
    public @NotNull String getAuthority() {
        return "ROLE_" + name();
    }

}
