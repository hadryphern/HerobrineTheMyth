package net.herobrinemyth.entity;

import net.herobrinemyth.network.Fx;
import net.herobrinemyth.registry.ModEntities;
import net.herobrinemyth.server.Fear;
import net.herobrinemyth.server.HauntData;
import net.herobrinemyth.server.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Herobrine. He does not walk like you. He does not make noise like you.
 * He is only where you are not looking.
 */
public class HerobrineEntity extends Monster {

    public enum Mode {
        /** Far away, staring. Gone when you look too long or come close. */
        STALK(true),
        /** Half hidden behind a tree. Gone the moment you see him. */
        PEEK(true),
        /** In the dark, no eyes. You know he is there. Eyes light up if you stare. */
        DARK(true),
        /** Right behind you. Turn around. */
        BEHIND(true),
        /** Only moves when you are not looking. */
        APPROACH(true),
        /** Runs at you, screaming. */
        CHASE(false),
        /** Actually fights. */
        FIGHT(false),
        /** An illusion. One of many. */
        CLONE(true),
        /** Was waiting behind the block you mined. */
        SCARE(true),
        /** On top of his shrine. */
        SHRINE(true),
        /** Watching you sleep. */
        WATCH(true),
        /** Stands still while your body walks to him. */
        LURE(true),
        /** iLoveYou's ghost: 17 blocks away with a redstone torch. Take one step towards him and he is gone. */
        GHOST(true),
        /** iLoveYou's thief: standing on your chest while you are away. */
        THIEF(true),
        /** iLoveYou's diamond event: "I have something for you." He digs down. You follow. */
        GUIDE(true),
        /** HerobrineAI's haunt: keeps reappearing around you, sounds, bats, a wolf. */
        HAUNT(true),
        /** HerobrineAI's attack: appears next to you, hits, hides in smoke, again and again. */
        STALKER(true),
        /** HerobrineAI's walking mode: just walking around the world. If you get close... */
        WANDER(false),
        /** Stands there for a moment. Nothing else. */
        ECHO(true),
        /** A plain Steve. The body never moves. The head follows you. */
        STEVE(true),
        /** Behind a tree. Look at him and he steps behind the trunk. */
        TREE(true);

        public final boolean glide;

        Mode(boolean glide) {
            this.glide = glide;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(HerobrineEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(HerobrineEntity.class, EntityDataSerializers.BYTE);
    private static final int FLAG_EYES = 1, FLAG_TILT = 2;

    @Nullable
    private UUID targetId;
    /** Rarely, everyone can see him, not only the one he is haunting. */
    public boolean shared;
    private int life;
    private int maxLife = 20 * 30;
    private int lookedTicks;
    private int lookLimit = 10;
    private int endTimer = -1;
    private int attackCooldown;
    private int nextTeleport = 80;
    private int twitch;
    private float twitchYaw;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuck;

    public HerobrineEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 8.0);
    }

    /** Places Herobrine for one player. */
    @Nullable
    public static HerobrineEntity spawn(ServerPlayer target, Vec3 pos, Mode mode, int maxLife) {
        ServerLevel level = target.serverLevel();
        HerobrineEntity h = ModEntities.HEROBRINE.get().create(level);
        if (h == null) return null;
        float yaw = Spots.yawTo(pos, target.position());
        h.moveTo(pos.x, pos.y, pos.z, yaw, Spots.pitchTo(pos.add(0, 1.62, 0), target.getEyePosition()));
        h.setYHeadRot(yaw);
        h.yBodyRot = yaw;
        h.targetId = target.getUUID();
        h.setMode(mode);
        h.maxLife = maxLife;
        boolean totem = HauntData.get(level.getServer()).totem;
        h.setEyes(mode == Mode.SHRINE || mode == Mode.FIGHT || (net.herobrinemyth.Config.GLOWING_EYES.get() && mode != Mode.DARK)
                || (totem && (mode == Mode.CHASE || mode == Mode.STALKER)));
        // sightings never last longer than the config allows
        if (mode == Mode.STALK || mode == Mode.PEEK || mode == Mode.DARK || mode == Mode.WATCH || mode == Mode.GHOST || mode == Mode.ECHO)
            maxLife = Math.min(maxLife, 20 * net.herobrinemyth.Config.SIGHTING_SECONDS.get());
        h.maxLife = maxLife;
        h.lookLimit = switch (mode) {
            case CLONE -> 1 + h.random.nextInt(6);
            case PEEK -> 3 + h.random.nextInt(6);
            case WATCH -> 6 + h.random.nextInt(10);
            default -> 6 + h.random.nextInt(30);
        };
        if (mode == Mode.SCARE || mode == Mode.GUIDE) h.setNoGravity(true);
        if (mode != Mode.LURE && mode != Mode.SCARE && Fear.shared(0.1)) h.shared = true;
        if (mode == Mode.GHOST || mode == Mode.THIEF || mode == Mode.GUIDE || mode == Mode.WANDER || h.random.nextInt(5) == 0) {
            // the classic Herobrine held a redstone torch
            h.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE_TORCH));
            h.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0F);
        }
        if (mode == Mode.GHOST || mode == Mode.THIEF || mode == Mode.WANDER || mode == Mode.HAUNT) h.lookLimit = 20 * 60 * 10;
        if (HauntData.get(level.getServer()).totem) {
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
            h.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(11.0);
            h.setHealth(500F);
        }
        level.addFreshEntity(h);
        return h;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODE, (byte) 0);
        this.entityData.define(DATA_FLAGS, (byte) FLAG_EYES);
    }

    public Mode getMode() {
        int i = this.entityData.get(DATA_MODE);
        Mode[] v = Mode.values();
        return i >= 0 && i < v.length ? v[i] : Mode.STALK;
    }

    public void setMode(Mode m) {
        this.entityData.set(DATA_MODE, (byte) m.ordinal());
    }

    private void setFlag(int flag, boolean on) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, (byte) (on ? (b | flag) : (b & ~flag)));
    }

    public boolean eyesGlow() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_EYES) != 0;
    }

    public void setEyes(boolean on) {
        setFlag(FLAG_EYES, on);
    }

    public boolean tilted() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_TILT) != 0;
    }

    public void setTilt(boolean on) {
        setFlag(FLAG_TILT, on);
    }

    @Nullable
    public UUID targetId() {
        return targetId;
    }

    private boolean lifeFromNbt;

    /**
     * Lets /summon place him exactly how you want, e.g. for videos:
     * /summon herobrinemyth:herobrine ~ ~ ~ {HBMode:"SHRINE",HBLife:600,HBEyes:1b}
     */
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HBMode")) {
            try {
                Mode m = Mode.valueOf(tag.getString("HBMode").toUpperCase(java.util.Locale.ROOT));
                setMode(m);
                setEyes(m != Mode.DARK);
                if (m == Mode.SCARE) setNoGravity(true);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (tag.contains("HBLife")) {
            maxLife = Math.max(1, tag.getInt("HBLife"));
            lifeFromNbt = true;
        }
        if (tag.contains("HBEyes")) setEyes(tag.getBoolean("HBEyes"));
        if (tag.contains("HBLook")) lookLimit = tag.getInt("HBLook");
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    protected void registerGoals() {
        // He needs no goals. He has you.
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) serverTick();
    }

    @Nullable
    private ServerPlayer target() {
        if (targetId == null || !(level() instanceof ServerLevel sl)) return null;
        Player p = sl.getPlayerByUUID(targetId);
        return p instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator() ? sp : null;
    }

    private void serverTick() {
        ServerPlayer t = target();
        if (t == null) {
            if (targetId == null) {
                // spawned by egg or command: pick someone
                Player p = level().getNearestPlayer(this, 96);
                if (p instanceof ServerPlayer sp) {
                    targetId = sp.getUUID();
                    if (!lifeFromNbt) maxLife = 20 * 60;
                    return;
                }
                if (++life > 200) discard();
                return;
            }
            vanish(false);
            return;
        }
        life++;
        if (endTimer > 0 && --endTimer == 0) {
            vanish(false);
            return;
        }
        Mode mode = getMode();
        double dist = this.distanceTo(t);
        boolean looked = Spots.isLookingAt(t, this, mode == Mode.BEHIND ? 0.55 : 0.88);
        if (looked) lookedTicks++;

        if (mode.glide && mode != Mode.APPROACH && mode != Mode.STEVE) {
            this.getNavigation().stop();
            stare(t);
        }

        switch (mode) {
            case STALK, WATCH -> {
                if (lookedTicks >= lookLimit) vanish(false);
                else if (dist < 5 && mode == Mode.STALK) vanish(false);
                else if (dist < 3) vanish(false);
            }
            case CLONE -> {
                if (lookedTicks >= lookLimit || dist < 6) vanish(true);
            }
            case PEEK -> {
                if (lookedTicks >= lookLimit || dist < 7) vanish(false);
            }
            case DARK -> {
                // just a shape. stare long enough, or come close, and there is nothing there.
                if (lookedTicks >= 40 + lookLimit || dist < 6) vanish(false);
            }
            case BEHIND -> {
                if (rushing) {
                    // he runs at you and taps you. then he is not there.
                    this.getNavigation().moveTo(t, 2.4);
                    if (dist < 1.6 || life > rushStart + 40) {
                        if (dist < 1.6) {
                            this.swing(InteractionHand.MAIN_HAND);
                            Fear.hurt(t, this.damageSources().mobAttack(this), 1F);
                            t.knockback(0.6, getX() - t.getX(), getZ() - t.getZ());
                        }
                        vanish(false);
                    }
                } else if (looked && lookedTicks >= 2) {
                    if (random.nextInt(5) < 2) {
                        rushing = true;
                        rushStart = life;
                    } else {
                        vanish(false); // you turned. nothing. was there something?
                    }
                } else if (!looked && life % 30 == 0 && dist > 4) {
                    Vec3 b = Spots.behind(t, 2.2);
                    if (b != null) teleportTo(b.x, b.y, b.z);
                }
            }
            case APPROACH -> approach(t, looked, dist);
            case CHASE -> chase(t, dist);
            case FIGHT -> fight(t, dist);
            case SCARE -> {
                // behind the block you mined. sometimes it is a scream. sometimes he only looks.
                if (life == 1) scareLoud = random.nextInt(5) < 2;
                if (life == 8 && scareLoud) Fear.jumpscare(t);
                if (life >= (scareLoud ? 20 : 35)) vanish(false);
            }
            case SHRINE -> {
                if (life >= maxLife - 1 && level() instanceof ServerLevel sl) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                    if (bolt != null) {
                        bolt.moveTo(getX(), getY(), getZ());
                        bolt.setVisualOnly(true);
                        sl.addFreshEntity(bolt);
                    }
                }
            }
            case LURE -> {
                if (dist < 2.6) {
                    Fear.jumpscare(t);
                    Fear.fx(t, Fx.CLEAR, 1, 0F);
                    vanish(false);
                }
            }
            case GHOST -> {
                // one step towards him and he is gone
                if ((lastDist > 0 && dist < lastDist - 0.04) || dist > 50) vanish(false);
                lastDist = dist;
            }
            case THIEF -> {
                if (dist < 10) vanish(random.nextBoolean());
            }
            case HAUNT -> haunt(t, dist);
            case STALKER -> stalker(t);
            case WANDER -> wander(t, dist);
            case GUIDE -> guide(t, dist);
            case ECHO -> {
            }
            case STEVE -> steve(t);
            case TREE -> {
                if (hiding > 0) {
                    if (--hiding == 0) vanish(false);
                } else if ((looked && lookedTicks >= 3) || dist < 8) {
                    if (anchor != null && dist >= 8) {
                        // step behind the trunk, away from you
                        Vec3 trunk = Vec3.atBottomCenterOf(anchor);
                        Vec3 away = trunk.subtract(t.position());
                        away = new Vec3(away.x, 0, away.z).normalize();
                        Vec3 behind = trunk.add(away.scale(0.95));
                        this.teleportTo(behind.x, getY(), behind.z);
                        hiding = 14;
                    } else {
                        vanish(false);
                    }
                }
            }
        }

        if (!isRemoved() && life >= maxLife) vanish(mode == Mode.FIGHT);
    }

    /** Turn everything towards the player. */
    private void stare(ServerPlayer t) {
        this.getLookControl().setLookAt(t.getX(), t.getEyeY(), t.getZ(), 360F, 360F);
        float yaw = Spots.yawTo(position(), t.position());
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.setYHeadRot(yaw);
        this.setXRot(Spots.pitchTo(getEyePosition(), t.getEyePosition()));
    }

    private boolean rushing, scareLoud;
    private BlockPos anchor;
    private int hiding;

    public void setAnchor(BlockPos trunk) {
        this.anchor = trunk;
    }

    /** The body stays where it is. Only the head turns to follow you, slowly, always. */
    private void steve(ServerPlayer t) {
        this.getNavigation().stop();
        float want = Spots.yawTo(position(), t.position());
        float diff = Mth.wrapDegrees(want - getYRot());
        if (Math.abs(diff) > 80) setYRot(getYRot() + Math.signum(diff) * Math.min(Math.abs(diff) - 80, 3F));
        this.yBodyRot = getYRot();
        this.setYHeadRot(Mth.approachDegrees(getYHeadRot(), want, 4F));
        this.setXRot(Spots.pitchTo(getEyePosition(), t.getEyePosition()));
        // small steps towards you. more of them when you are not looking. he stops at arm's length.
        double dist = distanceTo(t);
        boolean looked = Spots.isLookingAt(t, this, 0.9);
        if (dist > 2.6 && life > 60 && life % (looked ? 50 : 25) == 0 && random.nextInt(looked ? 3 : 2) == 0) {
            Vec3 dir = t.position().subtract(position());
            dir = new Vec3(dir.x, 0, dir.z).normalize().scale(looked ? 0.22 : 0.45);
            Vec3 next = position().add(dir);
            BlockPos stand = Spots.standable((ServerLevel) level(), Mth.floor(next.x), getBlockY(), Mth.floor(next.z), 1);
            if (stand != null) this.teleportTo(next.x, stand.getY(), next.z);
        }
        if (life % 20 == 0 && random.nextInt(18) == 0) {
            String[] s = {"entity.villager.ambient", "entity.zombie_villager.ambient", "ambient.cave", "entity.villager.no", "block.portal.ambient"};
            Fear.soundReverb(t, s[random.nextInt(s.length)], getEyePosition(), 0.6F, 0.5F);
        }
    }

    private int rushStart;

    private void approach(ServerPlayer t, boolean looked, double dist) {
        this.getNavigation().stop();
        stare(t);
        if (dist < 1.9) {
            Fear.jumpscare(t);
            Fear.hurt(t, this.damageSources().mobAttack(this), 4F);
            vanish(false);
            return;
        }
        if (looked || life % 3 != 0) return;
        Vec3 dir = t.position().subtract(position());
        dir = new Vec3(dir.x, 0, dir.z).normalize().scale(Math.min(1.1, dist - 1.2));
        Vec3 next = position().add(dir);
        BlockPos stand = Spots.standable((ServerLevel) level(), Mth.floor(next.x), getBlockY(), Mth.floor(next.z), 2);
        if (stand != null) {
            this.teleportTo(next.x, stand.getY(), next.z);
        } else if (life % 30 == 0) {
            Vec3 b = Spots.behind(t, Math.max(2, dist * 0.6));
            if (b != null) teleportTo(b.x, b.y, b.z);
        }
    }

    private void chase(ServerPlayer t, double dist) {
        if (life == 1) {
            // one classic scream, far and slow, then only your heart and his steps
            Fear.soundReverb(t, random.nextBoolean() ? "entity.ghast.scream" : "entity.horse.death", position(), 2.5F, 0.5F);
            Fear.fx(t, Fx.HEARTBEAT, maxLife + 20, 1F);
        }

        this.getNavigation().moveTo(t, 1.45);
        // BasicHerobrine: he leaves a trail of fire
        HauntData hd = HauntData.get(t.getServer());
        int fireDay = net.herobrinemyth.Config.FIRE_START_DAY.get();
        boolean fire = hd.totem || (fireDay > 0 && hd.level(t.getUUID()) >= fireDay);
        if (life % 3 == 0 && net.herobrinemyth.Config.ALLOW_GRIEFING.get() && fire) {
            BlockPos feet = blockPosition();
            if (level().getBlockState(feet).isAir() && Spots.solidFloor((ServerLevel) level(), feet.below()))
                level().setBlock(feet, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
        }
        if (life % 20 == 0) {
            if (position().distanceTo(lastPos) < 0.6) stuck++;
            else stuck = 0;
            lastPos = position();
            if (stuck >= 2 || dist > 28) {
                Vec3 b = Spots.behind(t, 5);
                if (b != null) teleportTo(b.x, b.y, b.z);
                stuck = 0;
            }
        }
        if (dist < 1.7) {
            Fear.hurt(t, this.damageSources().mobAttack(this), 6F);
            if (random.nextBoolean()) Fear.jumpscare(t);
            vanish(false);
        }
    }

    private void fight(ServerPlayer t, double dist) {
        setEyes(true);
        this.setTarget(t);
        this.getLookControl().setLookAt(t, 60F, 60F);
        if (attackCooldown > 0) attackCooldown--;
        if (dist > 2.2) {
            this.getNavigation().moveTo(t, 1.25);
        } else {
            this.getNavigation().stop();
            if (attackCooldown == 0) {
                this.swing(InteractionHand.MAIN_HAND);
                Fear.hurt(t, this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
                attackCooldown = 14 + random.nextInt(10);
                if (random.nextInt(6) == 0) {
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 1.1, 0));
                    t.hurtMarked = true;
                    Fear.sound(t, "entity.ender_dragon.flap", t.position(), 1F, 0.6F);
                }
            }
        }
        if (--nextTeleport <= 0) {
            nextTeleport = 50 + random.nextInt(70);
            Vec3 b = Spots.behind(t, 2.5);
            if (b != null) {
                poofParticles();
                teleportTo(b.x, b.y, b.z);
                Fear.ear(t, "entity.enderman.teleport", 1F, 0.5F);
            }
            boolean angry = HauntData.get(t.getServer()).totem;
            if (angry && random.nextInt(3) == 0 && level() instanceof ServerLevel sl) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(t.getX() + random.nextGaussian() * 2, t.getY(), t.getZ() + random.nextGaussian() * 2);
                    sl.addFreshEntity(bolt);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the classic behaviours

    private double lastDist = -1;
    private int bats, wolves;

    private static final String[] CLASSIC_SOUNDS = {
            "entity.ghast.scream", "entity.wither.death", "entity.wither.hurt", "entity.bat.hurt",
            "entity.player.breath", "entity.player.hurt", "block.iron_door.open", "block.iron_door.close"
    };

    /** HerobrineAI's haunt: he keeps showing up around you. Get close and he is somewhere else. */
    private void haunt(ServerPlayer t, double dist) {
        if (life % 140 == 0 && random.nextBoolean()) {
            Fear.soundReverb(t, Fear.pick(CLASSIC_SOUNDS), t.position().add(random.nextGaussian() * 6, 1, random.nextGaussian() * 6), 0.6F, 0.6F);
            int r = random.nextInt(100);
            if (r >= 70 && r < 80 && bats < 2) {
                spawnBat(t.position().add(0, 1.5, 0));
                bats++;
            } else if (r >= 80 && r < 90 && wolves < 1) {
                net.herobrinemyth.server.Classic.angryWolf(t);
                wolves++;
            }
        }
        if (dist < 5 || life % 140 == 0) {
            poofParticles();
            Vec3 v = Spots.findWatchingSpot(t, 10, 20, 15, 10);
            if (v != null) teleportTo(v.x, v.y, v.z);
        }
    }

    /** HerobrineAI's attack: next to you, a hit, smoke, gone. Again. */
    private void stalker(ServerPlayer t) {
        int phase = life % 60;
        if (life == 1) Fear.fx(t, Fx.HEARTBEAT, maxLife, 1F);
        if (phase == 1) {
            Vec3 v = random.nextBoolean() ? Spots.behind(t, 2) : Spots.findHidden(t, 2, 3, 0.4, 15, 10);
            if (v != null) teleportTo(v.x, v.y, v.z);
            setInvisible(false);
            stare(t);
            Fear.ear(t, "entity.player.breath", 0.9F, 0.75F);
            if (random.nextInt(100) < 55) {
                Fear.hurt(t, this.damageSources().mobAttack(this), 4F);
            }
        } else if (phase == 31) {
            poofParticles();
            spawnBat(position().add(0, 1, 0));
            spawnBat(position().add(0, 1, 0));
            setInvisible(true);
        }
    }

    /** HerobrineAI's walking mode. He walks. If you come within 15 he stops and looks. Within 7, he comes for you. */
    private void wander(ServerPlayer t, double dist) {
        setEyes(true);
        if (dist <= 7) {
            poofParticles();
            ServerPlayer target = t;
            net.herobrinemyth.server.Scheduler.later(target, 40, net.herobrinemyth.server.Classic::attack);
            vanish(false);
        } else if (dist <= 15) {
            this.getNavigation().stop();
            stare(t);
        } else if (this.getNavigation().isDone() || life % 120 == 0) {
            if (random.nextInt(3) == 0) {
                double x = getX() + random.nextInt(31) - 15, z = getZ() + random.nextInt(31) - 15;
                this.getNavigation().moveTo(x, getY(), z, 0.55);
            }
        }
    }

    // iLoveYou's diamond event
    private BlockPos diamond;
    private int guideState;
    private final java.util.List<java.util.Map.Entry<BlockPos, BlockState>> dug = new java.util.ArrayList<>();

    public void setDiamond(BlockPos pos) {
        this.diamond = pos;
    }

    private void dig(BlockPos p) {
        BlockState s = level().getBlockState(p);
        if (s.isAir() || !net.herobrinemyth.server.Builder.replaceable((ServerLevel) level(), p)) return;
        dug.add(java.util.Map.entry(p.immutable(), s));
        level().setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    private void guide(ServerPlayer t, double dist) {
        if (diamond == null) {
            vanish(false);
            return;
        }
        switch (guideState) {
            case 0 -> { // waiting for you to come closer
                stare(t);
                if (dist < 7) guideState = 1;
            }
            case 1 -> { // digging down, one block at a time, while you follow
                if (life % 8 != 0 || dist > 10) return;
                BlockPos feet = blockPosition();
                if (feet.getY() <= diamond.getY()) {
                    BlockPos side = feet.relative(net.minecraft.core.Direction.WEST);
                    dig(side);
                    dig(side.above());
                    guideState = 2;
                    return;
                }
                BlockPos below = feet.below();
                dig(below);
                // a ladder for you, on the wall
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos wall = below.relative(d);
                    if (level().getBlockState(wall).isFaceSturdy(level(), wall, d.getOpposite())) {
                        level().setBlock(below, net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, d.getOpposite()), 3);
                        break;
                    }
                }
                teleportTo(getX(), getY() - 1, getZ());
                Fear.sound(t, "block.stone.break", position(), 0.8F, 0.8F);
            }
            case 2 -> { // waiting next to the diamonds
                stare(t);
                if (dist < 3) {
                    Fear.chat(t, Fear.t("herobrine.diamond.bye" + random.nextInt(3)));
                    guideState = 3;
                    java.util.List<java.util.Map.Entry<BlockPos, BlockState>> restore = new java.util.ArrayList<>(dug);
                    ServerLevel sl = (ServerLevel) level();
                    // and the way back up closes behind you
                    net.herobrinemyth.server.Scheduler.later(t, 15, sp -> {
                        net.minecraft.world.phys.AABB safe = sp.getBoundingBox().inflate(1.2);
                        for (int i = restore.size() - 1; i >= 0; i--) {
                            BlockPos p = restore.get(i).getKey();
                            if (safe.intersects(new net.minecraft.world.phys.AABB(p))) continue;
                            if (sl.getBlockState(p).isAir() || sl.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LADDER))
                                sl.setBlock(p, restore.get(i).getValue(), 3);
                        }
                    });
                    vanish(false);
                }
            }
            default -> {
            }
        }
    }

    private void spawnBat(Vec3 at) {
        if (!(level() instanceof ServerLevel sl)) return;
        net.minecraft.world.entity.ambient.Bat bat = EntityType.BAT.create(sl);
        if (bat == null) return;
        bat.moveTo(at.x, at.y, at.z);
        sl.addFreshEntity(bat);
    }

    // ------------------------------------------------------------------ leaving

    private void poofParticles() {
        ServerPlayer t = target();
        if (level() instanceof ServerLevel sl && t != null) {
            sl.sendParticles(t, ParticleTypes.LARGE_SMOKE, false, getX(), getY() + 1, getZ(), 30, 0.3, 0.8, 0.3, 0.01);
            sl.sendParticles(t, ParticleTypes.SMOKE, false, getX(), getY() + 1, getZ(), 30, 0.3, 0.8, 0.3, 0.02);
        }
    }

    /** Only the player he is haunting can see him. Your friends see nothing. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return shared || targetId == null || targetId.equals(player.getUUID());
    }

    /** He is gone. Was he ever there? */
    public void vanish(boolean poof) {
        if (isRemoved()) return;
        if (poof) poofParticles();
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (getMode() == Mode.STEVE && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (getMode() != Mode.FIGHT) {
            if (attacker instanceof ServerPlayer sp) {
                vanish(true);
                if (random.nextInt(3) == 0) {
                    Fear.chat(sp, Fear.t("herobrine.chat.hurt" + random.nextInt(4)));
                }
            }
            return false;
        }
        if (!(attacker instanceof Player)) return false;
        boolean r = super.hurt(source, amount);
        if (getHealth() < getMaxHealth() * 0.35F) {
            if (attacker instanceof ServerPlayer sp) {
                Fear.sound(sp, "entity.witch.celebrate", position(), 1F, 0.4F);
                Fear.chat(sp, Fear.t("herobrine.chat.flee" + random.nextInt(3)));
            }
            vanish(true);
        }
        return r;
    }

    // ------------------------------------------------------------------ vanilla overrides

    @Override
    public void calculateEntityAnimation(boolean flying) {
        if (getMode().glide) {
            this.walkAnimation.setSpeed(0F);
            this.walkAnimation.update(0F, 1F);
        } else {
            super.calculateEntityAnimation(flying);
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        Mode m = getMode();
        if (m == Mode.CHASE || m == Mode.FIGHT) super.playStepSound(pos, state);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }
}
