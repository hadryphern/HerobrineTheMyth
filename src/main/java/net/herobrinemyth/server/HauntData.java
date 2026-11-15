package net.herobrinemyth.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-wide haunting state, stored with the overworld. */
public class HauntData extends SavedData {
    private static final String NAME = "herobrinemyth";

    public boolean totem;
    public BlockPos totemPos = BlockPos.ZERO;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    /** Chests players have used, per dimension. Herobrine remembers where your things are. */
    private final Map<String, java.util.List<BlockPos>> chests = new HashMap<>();

    public java.util.List<BlockPos> chests(String dim) {
        return chests.getOrDefault(dim, java.util.List.of());
    }

    public void rememberChest(String dim, BlockPos pos) {
        java.util.List<BlockPos> list = chests.computeIfAbsent(dim, k -> new java.util.ArrayList<>());
        if (list.contains(pos)) return;
        list.add(pos.immutable());
        if (list.size() > 40) list.remove(0);
        setDirty();
    }

    public static final class PlayerState {
        // persisted
        public long playTicks;
        public long postTotemTicks;
        public int crashes;
        public int forcedLevel = -1;
        public boolean warnedOfTotem;
        /** Days lived through since he arrived. Day 1 is the first day. */
        public int days = 1;
        public long lastDayIndex = -1;
        /** How close his markers are to your home (see Director). */
        public int markerStep;

        // runtime only (server tick based)
        public final java.util.Map<String, Long> lastRun = new java.util.HashMap<>();
        public final java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>();
        public boolean introduced;
        public long introNext;
        public long nextMinor, nextMajor, busyUntil;
        public long nextCorrupt, nextChunk, nextSwarm, nextNoise, nextCrash, nextDeletionCheck;
        public long lastBlockScare;
        public boolean deletionStarted;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("play", playTicks);
            t.putLong("post", postTotemTicks);
            t.putInt("crashes", crashes);
            t.putInt("forced", forcedLevel);
            t.putBoolean("warned", warnedOfTotem);
            t.putInt("days", days);
            t.putLong("lastDay", lastDayIndex);
            t.putInt("marker", markerStep);
            return t;
        }

        static PlayerState load(CompoundTag t) {
            PlayerState s = new PlayerState();
            s.playTicks = t.getLong("play");
            s.postTotemTicks = t.getLong("post");
            s.crashes = t.getInt("crashes");
            s.forcedLevel = t.contains("forced") ? t.getInt("forced") : -1;
            s.warnedOfTotem = t.getBoolean("warned");
            s.days = t.contains("days") ? Math.max(1, t.getInt("days")) : 1;
            s.lastDayIndex = t.contains("lastDay") ? t.getLong("lastDay") : -1;
            s.markerStep = t.getInt("marker");
            return s;
        }
    }

    public static HauntData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HauntData::load, HauntData::new, NAME);
    }

    public PlayerState player(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerState());
    }

    /**
     * The day of the haunting for this player: 1 on the first day, growing every day.
     * Everything he can do unlocks by day (see Haunts). After the totem it is always 100.
     */
    public int level(UUID id) {
        PlayerState s = player(id);
        if (s.forcedLevel > 0) return s.forcedLevel;
        if (totem) return 100;
        if (net.herobrinemyth.ServerConfig.MODE.get() == net.herobrinemyth.ServerConfig.Mode.MINUTES) {
            return (int) Math.min(100000, 1 + s.playTicks / (1200L * net.herobrinemyth.ServerConfig.MINUTES_PER_STEP.get()));
        }
        return 1 + (s.days - 1) / net.herobrinemyth.ServerConfig.DAYS_PER_STEP.get();
    }

    public static HauntData load(CompoundTag tag) {
        HauntData d = new HauntData();
        d.totem = tag.getBoolean("totem");
        if (tag.contains("totemPos")) d.totemPos = NbtUtils.readBlockPos(tag.getCompound("totemPos"));
        CompoundTag ps = tag.getCompound("players");
        for (String k : ps.getAllKeys()) {
            try {
                d.players.put(UUID.fromString(k), PlayerState.load(ps.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag cs = tag.getCompound("chests");
        for (String dim : cs.getAllKeys()) {
            java.util.List<BlockPos> list = new java.util.ArrayList<>();
            for (long l : cs.getLongArray(dim)) list.add(BlockPos.of(l));
            d.chests.put(dim, list);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("totem", totem);
        tag.put("totemPos", NbtUtils.writeBlockPos(totemPos));
        CompoundTag ps = new CompoundTag();
        players.forEach((k, v) -> ps.put(k.toString(), v.save()));
        tag.put("players", ps);
        CompoundTag cs = new CompoundTag();
        chests.forEach((dim, list) -> cs.putLongArray(dim, list.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put("chests", cs);
        return tag;
    }
}
