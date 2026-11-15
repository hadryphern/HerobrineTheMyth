package net.herobrinemyth.server;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * HerobrineAI's temple artifacts: the Bow of Teleporting, the Sword of Lightning,
 * the Apple of Death and the Ancient Sword (he will not attack you while you carry it).
 */
public final class Artifacts {
    private static final String TAG = "HerobrineArtifact";

    public static ItemStack make(String kind) {
        ItemStack s;
        switch (kind) {
            case "bow" -> {
                s = new ItemStack(Items.BOW);
                s.enchant(Enchantments.FLAMING_ARROWS, 1);
                s.enchant(Enchantments.PUNCH_ARROWS, 1);
            }
            case "sword" -> {
                s = new ItemStack(Items.DIAMOND_SWORD);
                s.enchant(Enchantments.KNOCKBACK, 2);
                s.enchant(Enchantments.SHARPNESS, 2);
                s.enchant(Enchantments.UNBREAKING, 3);
            }
            case "apple" -> s = new ItemStack(Items.GOLDEN_APPLE);
            default -> {
                kind = "ancient";
                s = new ItemStack(Items.GOLDEN_SWORD);
                s.enchant(Enchantments.KNOCKBACK, 2);
                s.enchant(Enchantments.SHARPNESS, 2);
            }
        }
        s.getOrCreateTag().putString(TAG, kind);
        s.setHoverName(Component.translatable("item.herobrinemyth.artifact." + kind).withStyle(ChatFormatting.DARK_RED));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("item.herobrinemyth.artifact.lore").withStyle(ChatFormatting.GRAY))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("item.herobrinemyth.artifact." + kind + ".desc").withStyle(ChatFormatting.DARK_GRAY))));
        s.getOrCreateTagElement("display").put("Lore", lore);
        return s;
    }

    public static ItemStack random() {
        int r = Fear.RNG.nextInt(16);
        return make(r < 4 ? "bow" : r < 8 ? "sword" : r < 12 ? "apple" : "ancient");
    }

    public static String kind(ItemStack s) {
        return s.hasTag() && s.getTag().contains(TAG) ? s.getTag().getString(TAG) : "";
    }

    /** Carrying the Ancient Sword keeps his attacks away. Only his attacks. */
    public static boolean protectedFromHim(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if ("ancient".equals(kind(s))) return true;
        return "ancient".equals(kind(p.getOffhandItem()));
    }

    // ------------------------------------------------------------------ powers

    @SubscribeEvent
    public static void onArrow(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer p)) return;
        for (InteractionHand hand : InteractionHand.values()) {
            if ("bow".equals(kind(p.getItemInHand(hand)))) {
                arrow.addTag("hb_teleport");
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !arrow.getTags().contains("hb_teleport")) return;
        if (!(arrow.getOwner() instanceof ServerPlayer p) || p.level() != arrow.level()) return;
        arrow.removeTag("hb_teleport");
        Vec3 at = event.getRayTraceResult().getLocation();
        p.teleportTo(p.serverLevel(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        p.level().playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 0.8F);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !"sword".equals(kind(p.getMainHandItem()))) return;
        if (Fear.RNG.nextBoolean()) strike(p.serverLevel(), event.getTarget().position());
    }

    public static void strike(ServerLevel l, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        l.addFreshEntity(bolt);
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !"apple".equals(kind(event.getItem()))) return;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(20))) {
            if (e instanceof LivingEntity le && e instanceof Enemy) le.kill();
        }
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.WITHER_DEATH, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private Artifacts() {}
}
