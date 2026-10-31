package dev.geo.sockschunkfix;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;

/**
 * Diagnostic: reproduces the call that used to abort chunk packets
 * ({@code BlockEntity#toInitialChunkDataNbt()} - MCPitanLib's override rebuilds a registry lookup
 * there) for the block entity at a position, and reports whether it now succeeds.
 *
 * <p>Usage in game / console: {@code /sockschunkfix selftest <x> <y> <z>}
 */
public final class SelfTestCommand {
    private SelfTestCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sockschunkfix")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("selftest")
                        .then(CommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(CommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes(SelfTestCommand::run))))));
    }

    private static int run(CommandContext<ServerCommandSource> ctx) {
        BlockPos pos = new BlockPos(
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y"),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z"));

        ServerWorld world = ctx.getSource().getWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) {
            ctx.getSource().sendError(Text.literal("No block entity at " + pos.toShortString()));
            return 0;
        }

        Method target = findChunkDataMethod(be.getClass());
        if (target == null) {
            ctx.getSource().sendError(Text.literal(
                    "No no-arg toInitialChunkDataNbt on " + be.getClass().getName()));
            return 0;
        }

        long start = System.nanoTime();
        try {
            target.setAccessible(true);
            Object result = target.invoke(be);
            long micros = (System.nanoTime() - start) / 1000L;
            int size = result instanceof NbtCompound nbt ? nbt.getSize() : -1;
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "OK: " + be.getClass().getSimpleName() + " serialized for chunk data in " + micros
                            + " us (" + size + " NBT keys) - no registry exception"), false);
            return 1;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            SocksChunkFix.LOGGER.error("Self-test failed for {}", be.getClass().getName(), cause);
            ctx.getSource().sendError(Text.literal(
                    "FAILED: " + cause.getClass().getSimpleName() + ": " + cause.getMessage()));
            return 0;
        }
    }

    private static Method findChunkDataMethod(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && NbtCompound.class.isAssignableFrom(m.getReturnType())
                        && (m.getName().equals("toInitialChunkDataNbt") || m.getName().equals("method_16887"))) {
                    return m;
                }
            }
        }
        return null;
    }
}
