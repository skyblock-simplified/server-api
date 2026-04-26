package dev.sbs.serverapi.security.openapi;

import dev.sbs.serverapi.security.ApiKeyRole;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enriches individual OpenAPI operations based on the {@link PreAuthorize} annotation.
 *
 * <p>Resolves {@code @PreAuthorize} with method-then-class precedence. When present, adds a
 * security requirement and 401/429 error responses; when the expression names specific roles
 * via {@code hasRole}, {@code hasAnyRole}, or {@code hasAuthority}, also adds a 403 response
 * and a role documentation note.</p>
 */
public class ApiKeyOperationCustomizer implements OperationCustomizer {

    private static final @NotNull Pattern ROLE_EXPRESSION = Pattern.compile(
        "(?:hasRole|hasAnyRole|hasAuthority|hasAnyAuthority)\\s*\\(([^)]*)\\)"
    );
    private static final @NotNull Pattern ROLE_LITERAL = Pattern.compile("['\"]([A-Z_]+)['\"]");

    @Override
    public @NotNull Operation customize(@NotNull Operation operation, @NotNull HandlerMethod handlerMethod) {
        Optional<PreAuthorize> annotation = resolveAnnotation(handlerMethod);

        if (annotation.isEmpty())
            return operation;

        operation.addSecurityItem(new SecurityRequirement().addList("X-API-Key"));

        ApiResponses responses = operation.getResponses();

        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        responses.addApiResponse("401", errorResponse(
            "Missing or invalid API key",
            401, "Unauthorized", "Missing X-API-Key header"));
        responses.addApiResponse("429", errorResponse(
            "Rate limit exceeded",
            429, "Too Many Requests", "Rate limit exceeded"));

        Set<ApiKeyRole> required = parseRoles(annotation.get().value());

        if (!required.isEmpty()) {
            String qualifyingRoles = resolveQualifyingRoles(required);

            responses.addApiResponse("403", errorResponse(
                "Insufficient permissions - requires one of: " + qualifyingRoles,
                403, "Forbidden", "Insufficient permissions"));

            String existing = operation.getDescription();
            String roleNote = "\n\n**Required roles** (any one of): `" + qualifyingRoles + "`";
            operation.setDescription(existing != null ? existing + roleNote : roleNote.strip());
        }

        return operation;
    }

    private static @NotNull Optional<PreAuthorize> resolveAnnotation(@NotNull HandlerMethod handlerMethod) {
        PreAuthorize annotation = handlerMethod.getMethodAnnotation(PreAuthorize.class);

        if (annotation == null)
            annotation = handlerMethod.getBeanType().getAnnotation(PreAuthorize.class);

        return Optional.ofNullable(annotation);
    }

    /**
     * Extracts {@link ApiKeyRole} constants named in role-based SpEL expressions like
     * {@code hasRole('USER')}, {@code hasAnyRole('ADMIN','USER')}, or
     * {@code hasAuthority('ROLE_USER')}. Non-role expressions yield an empty set,
     * which is treated as "authenticated only".
     */
    private static @NotNull Set<ApiKeyRole> parseRoles(@Nullable String expression) {
        if (expression == null || expression.isBlank())
            return EnumSet.noneOf(ApiKeyRole.class);

        Set<ApiKeyRole> roles = EnumSet.noneOf(ApiKeyRole.class);
        Matcher exprMatcher = ROLE_EXPRESSION.matcher(expression);

        while (exprMatcher.find()) {
            Matcher literalMatcher = ROLE_LITERAL.matcher(exprMatcher.group(1));

            while (literalMatcher.find()) {
                String literal = literalMatcher.group(1);
                String roleName = literal.startsWith("ROLE_") ? literal.substring("ROLE_".length()) : literal;

                try {
                    roles.add(ApiKeyRole.valueOf(roleName));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return roles;
    }

    /**
     * Returns the comma-separated list of {@link ApiKeyRole}s that satisfy the requirement,
     * accounting for the declaration-order hierarchy (earlier constants inherit later
     * constants' authorities).
     */
    private static @NotNull String resolveQualifyingRoles(@NotNull Set<ApiKeyRole> required) {
        int minOrdinal = required.stream().mapToInt(Enum::ordinal).min().orElse(Integer.MAX_VALUE);

        return Arrays.stream(ApiKeyRole.values())
            .filter(role -> role.ordinal() <= minOrdinal)
            .map(ApiKeyRole::name)
            .collect(Collectors.joining(", "));
    }

    private static @NotNull ApiResponse errorResponse(
            @NotNull String description,
            int status,
            @NotNull String error,
            @NotNull String message) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("status", status);
        example.put("error", error);
        example.put("message", message);
        example.put("path", "GET /example");

        return new ApiResponse()
            .description(description)
            .content(new Content().addMediaType(
                "application/json",
                new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                    .example(example)
            ));
    }

}
