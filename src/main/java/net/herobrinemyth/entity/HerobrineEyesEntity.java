package net.herobrinemyth.entity;

import net.herobrinemyth.registry.ModEntities;
import net.herobrinemyth.server.Spots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Two white eyes in the dark. Nothing else. */
public class HerobrineEyesEntity extends Entity {
    @Nullable
    private UUID targetId;
    private int life;
    private int maxLife = 20 * 40;
    private int looked;

    public HerobrineEyesEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Nullable
    public static HerobrineEyesEntity spawn(ServerPlayer target, Vec3 eyePos, int maxLife) {
        ServerLevel level = target.serverLevel();
        HerobrineEyesEntity e = ModEntities.EYES.get().create(level);
        if (e == null) return null;
        e.moveTo(eyePos.x, eyePos.y - 0.2, eyePos.z, Spots.yawTo(eyePos, target.position()), 0);
        e.targetId = target.getUUID();
        e.maxLife = maxLife;
        level.addFreshEntity(e);
        return e;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        Player p = targetId == null ? level().getNearestPlayer(this, 64) : sl.getPlayerByUUID(targetId);
        if (!(p instanceof ServerPlayer sp) || ++life > maxLife) {
            discard();
            return;
        }
        this.setYRot(Spots.yawTo(position(), sp.position()));
        double d = distanceTo(sp);
        if (d < 5.5 || Spots.light(sl, blockPosition()) > 7) {
            discard();
            return;
        }
        if (Spots.isLookingAt(sp, position().add(0, 0.2, 0), 0.97)) {
            if (++looked > 80) discard();
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return targetId == null || targetId.equals(player.getUUID());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }
}
