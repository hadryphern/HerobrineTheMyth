package net.herobrinemyth.server;

import net.herobrinemyth.Config;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEntity.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;

/** You broke something of his. "Don't do that again..." */
public final class Punish {

    public static void broke(ServerPlayer p, BlockPos pos) {
        if (!Config.ENABLED.get() || !Config.PUNISH_BREAKING.get()) return;
        if (p.isCreative() && !Config.HAUNT_CREATIVE.get()) return;
        HauntData data = HauntData.get(p.getServer());
        if (!data.unmark(pos)) return;
        HauntData.PlayerState st = data.player(p.getUUID());
        long now = Scheduler.now();
        if (st.lastPunish != 0 && now - st.lastPunish < 20 * 25) return;
        // not every block: but often enough that you learn
        if (Fear.RNG.nextInt(3) == 0) return;
        st.lastPunish = now;
        Scheduler.later(p, 10 + Fear.RNG.nextInt(30), sp -> punish(sp, pos));
    }

    private static void punish(ServerPlayer p, BlockPos pos) {
        Fear.chatAll(p, Component.translatable("herobrine.punish"));
        Fear.unmute(p, p.position(), 40, 20 * 20);
        ServerLevel l = p.serverLevel();
        switch (Fear.RNG.nextInt(8)) {
            case 0 -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
                if (bolt != null) {
                    Vec3 at = p.position().add(Fear.RNG.nextGaussian(), 0, Fear.RNG.nextGaussian());
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(Fear.RNG.nextBoolean());
                    l.addFreshEntity(bolt);
                }
            }
            case 1 -> {
                Vec3 v = Spots.findWatchingSpot(p, 4, 8, 15, 30);
                if (v == null) v = Spots.behind(p, 3);
                if (v != null) HerobrineEntity.spawn(p, v, Fear.RNG.nextBoolean() ? Mode.FIGHT : Mode.CHASE, 20 * 30);
            }
            case 2 -> Possession.spawnWave(p, 3 + Fear.RNG.nextInt(4), 20 * 2);
            case 3 -> Human.lavaRain(p);
            case 4 -> {
                int n = 4 + Fear.RNG.nextInt(5);
                for (int i = 0; i < n; i++) {
                    Silverfish s = EntityType.SILVERFISH.create(l);
                    if (s == null) continue;
                    s.moveTo(p.getX() + Fear.RNG.nextGaussian() * 2, p.getY() + 0.5, p.getZ() + Fear.RNG.nextGaussian() * 2);
                    s.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 2));
                    s.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1));
                    s.setTarget(p);
                    l.addFreshEntity(s);
                }
            }
            case 5 -> {
                p.setDeltaMovement(p.getDeltaMovement().add(0, 2.2, 0));
                p.hurtMarked = true;
                Fear.sound(p, "entity.ender_dragon.flap", p.position(), 1F, 0.5F);
            }
            case 6 -> {
                if (Config.ALLOW_CAVE_TELEPORT.get() && Caves.teleport(p, true)) {
                    Scheduler.later(p, 40, sp -> {
                        Vec3 v = Spots.findWatchingSpot(sp, 4, 9, 15, 30);
                        if (v != null) HerobrineEntity.spawn(sp, v, Mode.WATCH, 20 * 20);
                    });
                } else {
                    Fear.jumpscare(p);
                }
            }
            default -> {
                Fear.loudJumpscare(p);
                Fear.hurt(p, p.damageSources().magic(), 4F);
            }
        }
    }

    private Punish() {}
}
