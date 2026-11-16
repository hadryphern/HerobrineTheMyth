package net.herobrinemyth.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEyesEntity;
import net.herobrinemyth.network.Fx;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.List;

/**
 * /herobrine trigger &lt;event&gt; [players]
 * /herobrine build &lt;structure&gt; [players]
 * /herobrine totem activate|reset
 * /herobrine level &lt;players&gt; &lt;0-10&gt;
 * /herobrine calm [players]
 * /herobrine status
 */
public final class HerobrineCommand {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("herobrine")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Haunts.ALL.keySet(), b))
                                .executes(c -> trigger(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> trigger(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("build")
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Builder.STRUCTURES.keySet(), b))
                                .executes(c -> build(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> build(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(Commands.literal("totem")
                        .then(Commands.literal("activate").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Totem.activate(p, Totem.findNear(p.serverLevel(), p.blockPosition(), 10).orElse(p.blockPosition()));
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            HauntData data = HauntData.get(c.getSource().getServer());
                            data.totem = false;
                            data.setDirty();
                            c.getSource().sendSuccess(() -> Component.translatable("herobrine.cmd.reset"), true);
                            return 1;
                        })))
                .then(Commands.literal("day")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100000))
                                        .executes(HerobrineCommand::level))))
                .then(Commands.literal("calm")
                        .executes(c -> calm(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> calm(c, EntityArgument.getPlayers(c, "targets")))))
                .then(Commands.literal("jumpscare")
                        .executes(c -> {
                            Fear.jumpscare(c.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) Fear.jumpscare(p);
                            return 1;
                        })))
                .then(Commands.literal("fx")
                        .then(Commands.argument("type", IntegerArgumentType.integer(0, 13))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 20 * 600))
                                        .executes(c -> fx(c, 1F))
                                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(0F, 1F))
                                                .executes(c -> fx(c, FloatArgumentType.getFloat(c, "intensity")))))))
                .then(Commands.literal("say")
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
                            for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers())
                                Fear.chat(p, Component.literal(StringArgumentType.getString(c, "message")));
                            return 1;
                        })))
                .then(Commands.literal("bloodmoon").executes(c -> {
                    Director.startBloodMoon(c.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("status").executes(HerobrineCommand::status)));
    }

    private static int fx(CommandContext<CommandSourceStack> c, float intensity) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int type = IntegerArgumentType.getInteger(c, "type");
        int ticks = IntegerArgumentType.getInteger(c, "ticks");
        if (type == Fx.CRASH || type == Fx.DELETE_WORLD) {
            c.getSource().sendFailure(Component.literal("Use '/herobrine trigger crash' or 'take_world' for that."));
            return 0;
        }
        Fear.fxAt(p, type, ticks, intensity, p.getEyePosition().add(p.getViewVector(1F).scale(-6)), 0);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        String id = StringArgumentType.getString(c, "event");
        if (!Haunts.ALL.containsKey(id)) {
            c.getSource().sendFailure(Component.translatable("herobrine.cmd.unknown", id));
            return 0;
        }
        int ok = 0;
        for (ServerPlayer p : players) if (Haunts.trigger(id, p)) ok++;
        final int n = ok;
        if (n > 0) c.getSource().sendSuccess(() -> Component.translatable("herobrine.cmd.triggered", id, n), true);
        else c.getSource().sendFailure(Component.translatable("herobrine.cmd.failed", id));
        return n;
    }

    private static int build(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        String id = StringArgumentType.getString(c, "structure");
        var s = Builder.STRUCTURES.get(id);
        if (s == null) {
            c.getSource().sendFailure(Component.translatable("herobrine.cmd.unknown", id));
            return 0;
        }
        int ok = 0;
        for (ServerPlayer p : players) if (s.test(p)) ok++;
        final int n = ok;
        if (n > 0) c.getSource().sendSuccess(() -> Component.translatable("herobrine.cmd.triggered", id, n), true);
        else c.getSource().sendFailure(Component.translatable("herobrine.cmd.failed", id));
        return n;
    }

    private static int level(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int lvl = IntegerArgumentType.getInteger(c, "level");
        HauntData data = HauntData.get(c.getSource().getServer());
        for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) data.player(p.getUUID()).forcedLevel = lvl == 0 ? -1 : lvl;
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("herobrine.cmd.level", lvl), true);
        return 1;
    }

    private static int calm(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            Fear.fx(p, Fx.CLEAR, 1, 0F);
            Possession.releaseAround(p);
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(128),
                    e -> e instanceof HerobrineEntity || e instanceof HerobrineEyesEntity)) {
                e.discard();
            }
            HauntData.PlayerState st = HauntData.get(p.getServer()).player(p.getUUID());
            st.nextMajor = Scheduler.now() + 20 * 60 * 5;
            st.nextMinor = Scheduler.now() + 20 * 60;
        }
        c.getSource().sendSuccess(() -> Component.translatable("herobrine.cmd.calm"), true);
        return players.size();
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        HauntData data = HauntData.get(c.getSource().getServer());
        ServerPlayer p = c.getSource().getPlayer();
        String lvl = p == null ? "-" : String.valueOf(data.level(p.getUUID()));
        String mins = p == null ? "-" : String.valueOf(data.player(p.getUUID()).playTicks / 1200);
        c.getSource().sendSuccess(() -> Component.translatable("herobrine.cmd.status", String.valueOf(data.totem), lvl, mins), false);
        return 1;
    }

    private HerobrineCommand() {}
}
