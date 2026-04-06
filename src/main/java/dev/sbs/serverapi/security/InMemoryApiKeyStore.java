package dev.sbs.serverapi.security;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

/**
 * In-memory reference implementation of {@link ApiKeyStore} backed by a
 * {@link ConcurrentMap} keyed by {@link ApiKey#getKeyValue()}.
 *
 * <p>Instances are held directly in the map, so the identity contract of
 * {@link ApiKeyStore} is trivially satisfied: repeated lookups for the same
 * key value return the same {@code ApiKey} reference and its sliding-window
 * rate-limit state is preserved.
 *
 * <p>Suitable for tests, local development, and stopgap production wiring
 * before a persistent store is available.
 */
public class InMemoryApiKeyStore implements ApiKeyStore {

    private final @NotNull ConcurrentMap<String, ApiKey> apiKeys = Concurrent.newMap();

    /**
     * Constructs an empty store.
     */
    public InMemoryApiKeyStore() {}

    /**
     * Constructs a store pre-populated with the given keys.
     *
     * @param keys initial keys to register, indexed by {@link ApiKey#getKeyValue()}
     */
    public InMemoryApiKeyStore(@NotNull Collection<ApiKey> keys) {
        keys.forEach(this::put);
    }

    @Override
    public @NotNull Optional<ApiKey> findByKey(@NotNull String keyValue) {
        return Optional.ofNullable(apiKeys.get(keyValue));
    }

    /**
     * Registers or replaces a key, indexed by its {@link ApiKey#getKeyValue()}.
     *
     * @param apiKey the key to register
     * @return this store, for chaining
     */
    public @NotNull InMemoryApiKeyStore put(@NotNull ApiKey apiKey) {
        apiKeys.put(apiKey.getKeyValue(), apiKey);
        return this;
    }

    /**
     * Removes the key with the given value, if present.
     *
     * @param keyValue the key string to remove
     * @return this store, for chaining
     */
    public @NotNull InMemoryApiKeyStore remove(@NotNull String keyValue) {
        apiKeys.remove(keyValue);
        return this;
    }

    /**
     * Removes all registered keys.
     *
     * @return this store, for chaining
     */
    public @NotNull InMemoryApiKeyStore clear() {
        apiKeys.clear();
        return this;
    }

}
