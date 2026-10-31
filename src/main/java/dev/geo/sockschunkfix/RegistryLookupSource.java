package dev.geo.sockschunkfix;

import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;

/**
 * Supplies the registry lookup that MCPitanLib should have used in the first place.
 *
 * <p>On 1.20.1 {@code DynamicRegistryManager} already implements
 * {@link RegistryWrapper.WrapperLookup}, so the running server's registry manager can be handed to
 * {@code CompatRegistryLookup} directly instead of rebuilding every builtin registry.
 *
 * <p>The running server is captured by {@link mixin.MinecraftServerMixin}; no Fabric API dependency is
 * needed for that.
 */
public final class RegistryLookupSource {
    private static volatile MinecraftServer server;

    private RegistryLookupSource() {
    }

    /** Internal: called from the {@link MinecraftServer} mixin when a server object is created. */
    public static void setServer(MinecraftServer instance) {
        server = instance;
    }

    /** Internal: called from the {@link MinecraftServer} mixin when the server shuts down. */
    public static void clearServer() {
        server = null;
    }

    /**
     * @return the live server's registry manager, or {@code null} when no server is running (pure
     *     client, world closing, …). Callers must fall back to upstream behaviour on {@code null}
     *     rather than inventing a lookup.
     */
    public static RegistryWrapper.WrapperLookup current() {
        MinecraftServer instance = server;
        return instance == null ? null : instance.getRegistryManager();
    }
}
