package net.herobrinemyth.client;

import com.mojang.authlib.GameProfile;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Press TAB. There are two of you. The other one has no name, and his skin is completely black.
 * He has been there since the world began.
 */
@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
public final class TabStranger {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final ResourceLocation BLACK = new ResourceLocation(HerobrineMyth.MODID, "textures/entity/black_skin.png");
    private static ClientPacketListener addedTo;

    private static final class Stranger extends PlayerInfo {
        Stranger() {
            super(new GameProfile(ID, " "), false);
        }

        @Override
        public ResourceLocation getSkinLocation() {
            return BLACK;
        }

        @Override
        public Component getTabListDisplayName() {
            return Component.literal(" ");
        }

        @Override
        public GameType getGameMode() {
            return GameType.SURVIVAL;
        }

        @Override
        public int getLatency() {
            return 999;
        }
    }

    private static boolean tabWasDown;
    private static long lastCave;

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // you open the list and you see him there: a cave sound, loud, slow, echoing
        boolean down = mc.options.keyPlayerList.isDown() && mc.player != null && mc.screen == null;
        if (down && !tabWasDown && addedTo != null && Config.TAB_STRANGER.get() && mc.level != null
                && mc.level.getGameTime() - lastCave > 20 * 45) {
            lastCave = mc.level.getGameTime();
            ClientHorror.play(new ResourceLocation("ambient.cave"), net.minecraft.sounds.SoundSource.HOSTILE, 3.5F, 0.5F,
                    mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), true);
        }
        tabWasDown = down;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null || connection == addedTo || !Config.TAB_STRANGER.get()) return;
        try {
            Map<UUID, PlayerInfo> infos = (Map<UUID, PlayerInfo>) ObfuscationReflectionHelper.findField(ClientPacketListener.class, "f_104892_").get(connection);
            Set<PlayerInfo> listed = (Set<PlayerInfo>) ObfuscationReflectionHelper.findField(ClientPacketListener.class, "f_244156_").get(connection);
            Stranger s = new Stranger();
            infos.put(ID, s);
            listed.add(s);
        } catch (Exception e) {
            HerobrineMyth.LOGGER.debug("Could not add the stranger to the player list", e);
        }
        addedTo = connection;
    }

    private TabStranger() {}
}
