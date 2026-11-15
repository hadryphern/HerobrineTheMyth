package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ServerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Scheduler.tick();
        long now = Scheduler.now();
        Possession.tick(server);
        Corruption.tickEaters();
        Graveyard.tick(server);
        Corruption.worldTick(server, now);
        if (now % 20 == 0) Director.tick(server);
    }

    /** A new world: what was chosen on the "new world" screen goes into this world's config. */
    @SubscribeEvent
    public static void onAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        net.herobrinemyth.ServerConfig.Mode mode = net.herobrinemyth.ServerConfig.pendingMode;
        if (mode == null) return;
        net.herobrinemyth.ServerConfig.pendingMode = null;
        int amount = Math.max(1, net.herobrinemyth.ServerConfig.pendingAmount);
        net.herobrinemyth.ServerConfig.MODE.set(mode);
        if (mode == net.herobrinemyth.ServerConfig.Mode.DAYS) net.herobrinemyth.ServerConfig.DAYS_PER_STEP.set(amount);
        else net.herobrinemyth.ServerConfig.MINUTES_PER_STEP.set(amount);
        net.herobrinemyth.ServerConfig.SPEC.save();
        net.herobrinemyth.HerobrineMyth.LOGGER.info("This world: Herobrine gets stronger every {} {}", amount, mode == net.herobrinemyth.ServerConfig.Mode.DAYS ? "day(s)" : "minute(s)");
    }

    /** You hit an animal or a mob... and sometimes it becomes one of his. A ghast screams. */
    @SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Config.ENABLED.get()) return;
        if (!(event.getTarget() instanceof net.minecraft.world.entity.Mob mob) || mob instanceof net.herobrinemyth.entity.HerobrineEntity) return;
        if (Possession.isPossessed(mob)) return;
        HauntData data = HauntData.get(p.getServer());
        int day = data.level(p.getUUID());
        if (!data.totem && (data.player(p.getUUID()).days < 3 || p.isCreative())) return;
        double chance = data.totem ? 0.3 : Math.min(0.2, 0.08 + day * 0.004);
        if (Fear.RNG.nextDouble() >= chance) return;
        Possession.possess(mob, p, 12, 20 * 90, true);
        Fear.soundReverb(p, "entity.ghast.scream", mob.position(), 1.6F, 0.55F);
    }

    /** Almost nobody else lives in this world. */
    @SubscribeEvent
    public static void onFinalizeSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        var type = event.getSpawnType();
        if (type != net.minecraft.world.entity.MobSpawnType.NATURAL && type != net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION) return;
        var mob = event.getEntity();
        boolean hostile = mob instanceof net.minecraft.world.entity.monster.Enemy;
        double keep = hostile ? Config.HOSTILE_SPAWNS.get() : Config.PASSIVE_SPAWNS.get();
        if (Fear.RNG.nextDouble() >= keep) event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        Scheduler.clear();
        Possession.clear();
        Corruption.clear();
        Graveyard.clear();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        HerobrineCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Graveyard.rescue(p);
        HauntData data = HauntData.get(p.getServer());
        HauntData.PlayerState st = data.player(p.getUUID());
        st.nextMinor = 0;
        st.nextMajor = 0;
        st.nextCorrupt = 0;
        if (data.totem) {
            Scheduler.later(p, 100, sp -> {
                Fear.joined(sp);
                Scheduler.later(sp, 60, s2 -> Fear.chat(s2, Msg.totemChat(s2)));
            });
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Possession.onStartTracking(event);
    }

    /** Lighting the shrine. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = event.getItemStack();
        BlockState clicked = p.level().getBlockState(event.getPos());
        if (clicked.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            HauntData.get(p.getServer()).rememberChest(p.level().dimension().location().toString(), event.getPos());
        }
        if (clicked.is(net.minecraft.world.level.block.Blocks.JUKEBOX) && stack.is(Items.MUSIC_DISC_11)
                && !clicked.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) {
            Classic.discPlayed(p);
        }
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) return;
        BlockPos pos = event.getPos();
        Scheduler.later(p, 2, sp -> Totem.check(sp, pos));
    }

    /** The block you just broke. Something was behind it. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        if (Graveyard.isGraveyard(p.level()) && !p.isCreative()) {
            event.setCanceled(true);
            return;
        }
        if (!Config.ENABLED.get() || !Config.ALLOW_BLOCK_SCARE.get()) return;
        if (p.isCreative() && !Config.HAUNT_CREATIVE.get()) return;
        BlockState s = event.getState();
        if (!s.is(BlockTags.BASE_STONE_OVERWORLD) && !s.is(BlockTags.DIRT)) return;
        if (Math.abs(p.getXRot()) > 45) return;
        HauntData data = HauntData.get(p.getServer());
        HauntData.PlayerState st = data.player(p.getUUID());
        int lvl = data.level(p.getUUID());
        if (!data.totem && (lvl < 2 || st.playTicks < Config.GRACE_SECONDS.get() * 20L)) return;
        long now = Scheduler.now();
        long cooldown = data.totem ? 20 * 60 : 20 * 60 * 5;
        if (now - st.lastBlockScare < cooldown && st.lastBlockScare != 0) return;
        int chance = data.totem ? 45 : (Spots.isUnderground(p) ? 140 : 300);
        if (Fear.RNG.nextInt(chance) != 0) return;
        if (Haunts.blockScare(p, event.getPos())) st.lastBlockScare = now;
    }

    /** HerobrineAI: what you find in your chest after closing it. */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu menu) {
            Classic.onChestClosed(p, menu.getContainer());
        }
    }

    /** iLoveYou: your commands answer in garbage. */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var src = event.getParseResults().getContext().getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) return;
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        if (input.startsWith("herobrine")) return;
        boolean totem = HauntData.get(p.getServer()).totem;
        if (Garble.active() || (totem && Fear.RNG.nextInt(4) == 0)) {
            event.setCanceled(true);
            p.sendSystemMessage(Component.literal(Classic.garbage(7) + " " + Classic.garbage(10)).withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    /** Nothing can be changed in the graveyard. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Graveyard.isGraveyard(p.level()) && !p.isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer p) || !Config.ENABLED.get()) return;
        HauntData data = HauntData.get(p.getServer());
        if (data.totem) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.translatable("herobrine.nosleep"), true);
            Fear.ear(p, "ambient.cave", 1F, 0.5F);
            return;
        }
        int lvl = data.level(p.getUUID());
        if (lvl < 2 || Fear.RNG.nextInt(4) != 0) return;
        // HerobrineAI: going to bed means the graveyard, a haunting or a demon. Or he is just there when you wake up.
        switch (Fear.RNG.nextInt(4)) {
            case 0 -> Haunts.bedScare(p);
            case 1 -> Scheduler.later(p, 40, sp -> {
                if (sp.isSleeping()) sp.stopSleepInBed(true, true);
                if (lvl < 4 || !Graveyard.send(sp)) Haunts.bedScare(sp);
            });
            case 2 -> Scheduler.later(p, 40, sp -> {
                if (sp.isSleeping()) sp.stopSleepInBed(true, true);
                Classic.haunt(sp);
            });
            default -> Scheduler.later(p, 40, sp -> {
                if (sp.isSleeping()) sp.stopSleepInBed(true, true);
                Classic.demon(sp);
            });
        }
    }

    private ServerEvents() {}
}
