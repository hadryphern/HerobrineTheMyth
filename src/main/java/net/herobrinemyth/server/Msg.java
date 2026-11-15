package net.herobrinemyth.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Translation key counts (see lang files). */
public final class Msg {
    public static final int CHAT = 26;
    public static final int NAMED = 10;
    public static final int WHISPER = 22;
    public static final int SIGNS = 23;
    public static final int FLASH = 12;
    public static final int BOOK = 7;
    public static final int FIGHT = 6;
    public static final int TOTEM_CHAT = 18;

    public static Component chat(ServerPlayer p) {
        if (Fear.RNG.nextInt(3) == 0)
            return Component.translatable("herobrine.chat.named." + Fear.RNG.nextInt(NAMED), p.getGameProfile().getName());
        return Component.translatable("herobrine.chat." + Fear.RNG.nextInt(CHAT));
    }

    public static Component totemChat(ServerPlayer p) {
        return Component.translatable("herobrine.totem.chat." + Fear.RNG.nextInt(TOTEM_CHAT), p.getGameProfile().getName());
    }

    public static Component whisper() {
        return Component.translatable("herobrine.whisper." + Fear.RNG.nextInt(WHISPER));
    }

    public static Component flash() {
        return Component.translatable("herobrine.flash." + Fear.RNG.nextInt(FLASH));
    }

    /** A book. Written for you. */
    public static ItemStack book(ServerPlayer p) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "...");
        tag.putString("author", "Herobrine");
        ListTag pages = new ListTag();
        for (int i = 0; i < BOOK; i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.translatable("herobrine.book." + i, p.getGameProfile().getName()))));
        }
        tag.put("pages", pages);
        tag.putBoolean("resolved", true);
        return book;
    }

    /** HerobrineAI's one-page books, untitled, unsigned. */
    public static ItemStack classicBook(ServerPlayer p) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "");
        tag.putString("author", "");
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("herobrine.classicbook." + Fear.RNG.nextInt(3)))));
        tag.put("pages", pages);
        tag.putBoolean("resolved", true);
        return book;
    }

    private Msg() {}
}
