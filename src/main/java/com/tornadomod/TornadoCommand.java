package com.tornadomod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tornadomod.entity.StormEntity;
import com.tornadomod.entity.TornadoEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * /tornado spawn [tipo] [intensidad 0-5] [pos]
 * /tornado clear [radio]
 * /tornado info
 * /tornado grief <true|false>
 * /tornado natural <true|false>
 * /tornado help
 */
public class TornadoCommand {

    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("tornado", "fire", "supercell", "cyclone"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tornado")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> help(ctx))

                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx, StormType.TORNADO, 2, ctx.getSource().getPosition()))
                        .then(Commands.argument("tipo", StringArgumentType.word())
                                .suggests(TYPE_SUGGESTIONS)
                                .executes(ctx -> spawn(ctx, parseType(ctx), 2, ctx.getSource().getPosition()))
                                .then(Commands.argument("intensidad", IntegerArgumentType.integer(0, 5))
                                        .executes(ctx -> spawn(ctx, parseType(ctx),
                                                IntegerArgumentType.getInteger(ctx, "intensidad"),
                                                ctx.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> spawn(ctx, parseType(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "intensidad"),
                                                        Vec3Argument.getVec3(ctx, "pos")))))))

                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx, Double.MAX_VALUE))
                        .then(Commands.argument("radio", DoubleArgumentType.doubleArg(1.0D))
                                .executes(ctx -> clear(ctx, DoubleArgumentType.getDouble(ctx, "radio")))))

                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx)))

                .then(Commands.literal("grief")
                        .then(Commands.argument("valor", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "valor");
                                    Config.BREAK_BLOCKS.set(v);
                                    Config.BREAK_BLOCKS.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Destruccion de bloques: " + (v ? "ACTIVADA" : "DESACTIVADA")), true);
                                    return 1;
                                })))

                .then(Commands.literal("natural")
                        .then(Commands.argument("valor", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "valor");
                                    Config.NATURAL_STORMS.set(v);
                                    Config.NATURAL_STORMS.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Tormentas naturales: " + (v ? "ACTIVADAS" : "DESACTIVADAS")), true);
                                    return 1;
                                })))

                .then(Commands.literal("help")
                        .executes(ctx -> help(ctx))));
    }

    private static StormType parseType(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String s = StringArgumentType.getString(ctx, "tipo");
        StormType t = StormType.byId(s);
        if (t == null) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Tipo desconocido: " + s + ". Usa: tornado, fire, supercell, cyclone")).create();
        }
        return t;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, StormType type, int ef, Vec3 pos) {
        ServerLevel level = ctx.getSource().getLevel();
        Entity e = StormManager.spawn(level, type, ef, pos);
        if (e == null) {
            ctx.getSource().sendFailure(Component.literal("No se pudo crear el fenomeno."));
            return 0;
        }
        String msg = String.format("Creado: %s (intensidad %d) en %.0f %.0f %.0f. "
                        + "Los tornados se forman desde el suelo, espera unos segundos.",
                type.id, ef, pos.x, pos.y, pos.z);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx, double radius) {
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 origin = ctx.getSource().getPosition();
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if ((e instanceof TornadoEntity || e instanceof StormEntity)
                    && e.position().distanceTo(origin) <= radius) {
                toRemove.add(e);
            }
        }
        for (Entity e : toRemove) {
            e.discard();
        }
        int n = toRemove.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Eliminados " + n + " tornados/tormentas."), true);
        return n;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int count = 0;
        for (Entity e : level.getAllEntities()) {
            String line = null;
            if (e instanceof TornadoEntity t) {
                line = String.format("[%s EF%d] x=%.0f y=%.0f z=%.0f  formacion=%d%%",
                        t.getStormType().id, t.getEf(), t.getX(), t.getY(), t.getZ(),
                        Math.round(t.getGrowth() * 100.0F));
            } else if (e instanceof StormEntity s) {
                line = String.format("[%s EF%d] x=%.0f y=%.0f z=%.0f  formacion=%d%%",
                        s.getStormType().id, s.getEf(), s.getX(), s.getY(), s.getZ(),
                        Math.round(s.getGrowth() * 100.0F));
            }
            if (line != null) {
                final String l = line;
                ctx.getSource().sendSuccess(() -> Component.literal(l), false);
                count++;
            }
        }
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No hay tornados ni tormentas en esta dimension."), false);
        }
        return count;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        String[] lines = {
                "=== Tornado Mod ===",
                "/tornado spawn [tipo] [intensidad 0-5] [pos]  - tipos: tornado, fire, supercell, cyclone",
                "/tornado clear [radio]  - elimina tornados y tormentas",
                "/tornado info  - lista los que hay y sus coordenadas",
                "/tornado grief <true|false>  - destruccion de bloques",
                "/tornado natural <true|false>  - tormentas naturales"
        };
        for (String l : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(l), false);
        }
        return 1;
    }
}
