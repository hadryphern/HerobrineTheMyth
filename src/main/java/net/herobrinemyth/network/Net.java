package net.herobrinemyth.network;

import net.herobrinemyth.HerobrineMyth;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** All packets are server -> client. The server decides, the client suffers. */
public final class Net {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HerobrineMyth.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Effect.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Effect::encode).decoder(Effect::decode).consumerMainThread(Effect::handle).add();
        CHANNEL.messageBuilder(Sound.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sound::encode).decoder(Sound::decode).consumerMainThread(Sound::handle).add();
        CHANNEL.messageBuilder(Possessed.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Possessed::encode).decoder(Possessed::decode).consumerMainThread(Possessed::handle).add();
        CHANNEL.messageBuilder(Text.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Text::encode).decoder(Text::decode).consumerMainThread(Text::handle).add();
        CHANNEL.messageBuilder(Phantom.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Phantom::encode).decoder(Phantom::decode).consumerMainThread(Phantom::handle).add();
    }

    public static void send(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendTracking(Entity entity, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), msg);
    }

    // ------------------------------------------------------------------ effects

    /** Client side screen / audio / control effect. See {@link Fx}. */
    public record Effect(int type, int duration, float intensity, double x, double y, double z, int extra) {
        public Effect(int type, int duration, float intensity) {
            this(type, duration, intensity, 0, 0, 0, 0);
        }

        static void encode(Effect m, FriendlyByteBuf b) {
            b.writeVarInt(m.type);
            b.writeVarInt(m.duration);
            b.writeFloat(m.intensity);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeVarInt(m.extra);
        }

        static Effect decode(FriendlyByteBuf b) {
            return new Effect(b.readVarInt(), b.readVarInt(), b.readFloat(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt());
        }

        static void handle(Effect m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.herobrinemyth.client.ClientPackets.effect(m));
        }
    }

    /**
     * A sound only this player hears. When {@code relative} is true, x/y/z are offsets
     * in the player's view space: x = right, y = up, z = forward.
     */
    public record Sound(ResourceLocation sound, float volume, float pitch, double x, double y, double z,
                        boolean relative, int stopAfter, boolean reverb) {
        public Sound(ResourceLocation sound, float volume, float pitch, double x, double y, double z, boolean relative, int stopAfter) {
            this(sound, volume, pitch, x, y, z, relative, stopAfter, false);
        }

        static void encode(Sound m, FriendlyByteBuf b) {
            b.writeResourceLocation(m.sound);
            b.writeFloat(m.volume);
            b.writeFloat(m.pitch);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeBoolean(m.relative);
            b.writeVarInt(m.stopAfter);
            b.writeBoolean(m.reverb);
        }

        static Sound decode(FriendlyByteBuf b) {
            return new Sound(b.readResourceLocation(), b.readFloat(), b.readFloat(), b.readDouble(), b.readDouble(),
                    b.readDouble(), b.readBoolean(), b.readVarInt(), b.readBoolean());
        }

        static void handle(Sound m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.herobrinemyth.client.ClientPackets.sound(m));
        }
    }

    /** Marks a mob as possessed (white eyes) on the client. */
    public record Possessed(int entityId, boolean on) {
        static void encode(Possessed m, FriendlyByteBuf b) {
            b.writeVarInt(m.entityId);
            b.writeBoolean(m.on);
        }

        static Possessed decode(FriendlyByteBuf b) {
            return new Possessed(b.readVarInt(), b.readBoolean());
        }

        static void handle(Possessed m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.herobrinemyth.client.ClientPackets.possessed(m));
        }
    }

    /** On-screen text. style: 0 whisper, 1 big red, 2 subliminal flash, 3 corrupted. */
    public record Text(Component text, int duration, int style) {
        static void encode(Text m, FriendlyByteBuf b) {
            b.writeComponent(m.text);
            b.writeVarInt(m.duration);
            b.writeVarInt(m.style);
        }

        static Text decode(FriendlyByteBuf b) {
            return new Text(b.readComponent(), b.readVarInt(), b.readVarInt());
        }

        static void handle(Text m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.herobrinemyth.client.ClientPackets.text(m));
        }
    }

    /**
     * A sound with "someone" behind it: footsteps, mining, building. It lives on the client and
     * stops the instant the player looks towards it. kind: 0 steps, 1 mining, 2 placing, 3 knocking.
     */
    public record Phantom(int kind, double x, double y, double z, double dx, double dz, int duration,
                          ResourceLocation sound, ResourceLocation sound2) {
        static void encode(Phantom m, FriendlyByteBuf b) {
            b.writeVarInt(m.kind);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeDouble(m.dx);
            b.writeDouble(m.dz);
            b.writeVarInt(m.duration);
            b.writeResourceLocation(m.sound);
            b.writeResourceLocation(m.sound2);
        }

        static Phantom decode(FriendlyByteBuf b) {
            return new Phantom(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readVarInt(), b.readResourceLocation(), b.readResourceLocation());
        }

        static void handle(Phantom m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.herobrinemyth.client.Phantoms.start(m));
        }
    }

    private Net() {}
}
