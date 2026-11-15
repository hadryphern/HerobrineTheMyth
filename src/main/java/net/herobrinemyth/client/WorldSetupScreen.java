package net.herobrinemyth.client;

import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.ServerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shown right after "Create New World", before the usual world creation screen.
 * Every [__] days / every [__] minutes, Herobrine gets stronger. Plain vanilla widgets.
 */
public class WorldSetupScreen extends Screen {
    private final Screen parent;
    private final CreateWorldScreen next;
    private Radio days, minutes;
    private EditBox daysBox, minutesBox;
    private MultiLineLabel help = MultiLineLabel.EMPTY;
    private Button done;
    private boolean daysOn = true;
    private String daysValue = "1", minutesValue = "10";

    /** A checkbox that behaves like a radio button: one of the pair is always on. */
    private final class Radio extends Checkbox {
        Radio partner;

        Radio(int x, int y, boolean on) {
            super(x, y, 20, 20, Component.empty(), on, false);
        }

        void flip() {
            super.onPress();
        }

        @Override
        public void onPress() {
            if (selected()) return;
            super.onPress();
            if (partner.selected()) partner.flip();
            refresh();
        }
    }

    public WorldSetupScreen(Screen parent, CreateWorldScreen next) {
        super(Component.translatable("herobrine.setup.title"));
        this.parent = parent;
        this.next = next;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 130;
        int y = this.height / 2 - 52;
        days = new Radio(left + 60, y, daysOn);
        minutes = new Radio(left + 60, y + 30, !daysOn);
        days.partner = minutes;
        minutes.partner = days;
        int box = left + 60 + 24 + this.font.width(Component.translatable("herobrine.setup.every")) + 6;
        daysBox = number(box, y, daysValue);
        minutesBox = number(box, y + 30, minutesValue);
        addRenderableWidget(days);
        addRenderableWidget(minutes);
        addRenderableWidget(daysBox);
        addRenderableWidget(minutesBox);
        help = MultiLineLabel.create(this.font, Component.translatable("herobrine.setup.help"), 300);
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> finish())
                .bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 + 5, this.height - 28, 150, 20).build());
        refresh();
    }

    private EditBox number(int x, int y, String value) {
        EditBox e = new EditBox(this.font, x, y, 40, 20, Component.empty());
        e.setMaxLength(5);
        e.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        e.setValue(value);
        e.setResponder(s -> refresh());
        return e;
    }

    private int value(EditBox e) {
        try {
            return Math.max(1, Integer.parseInt(e.getValue()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void refresh() {
        if (days == null || daysBox == null || minutesBox == null) return;
        daysOn = days.selected();
        daysValue = daysBox.getValue();
        minutesValue = minutesBox.getValue();
        daysBox.setEditable(days.selected());
        minutesBox.setEditable(minutes.selected());
        if (done != null) done.active = value(days.selected() ? daysBox : minutesBox) > 0;
    }

    private void finish() {
        ServerConfig.pendingMode = days.selected() ? ServerConfig.Mode.DAYS : ServerConfig.Mode.MINUTES;
        ServerConfig.pendingAmount = value(days.selected() ? daysBox : minutesBox);
        Hook.passThrough = true;
        try {
            this.minecraft.setScreen(next);
        } finally {
            Hook.passThrough = false;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderDirtBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        int left = this.width / 2 - 130;
        int y = this.height / 2 - 52;
        drawFace(g, left - 6, y - 4, 52);
        int text = left + 60 + 24;
        int afterBox = daysBox.getX() + daysBox.getWidth() + 6;
        g.drawString(this.font, Component.translatable("herobrine.setup.every"), text, y + 6, days.selected() ? 0xFFFFFF : 0x808080);
        g.drawString(this.font, Component.translatable("herobrine.setup.days"), afterBox, y + 6, days.selected() ? 0xFFFFFF : 0x808080);
        g.drawString(this.font, Component.translatable("herobrine.setup.every"), text, y + 36, minutes.selected() ? 0xFFFFFF : 0x808080);
        g.drawString(this.font, Component.translatable("herobrine.setup.minutes"), afterBox, y + 36, minutes.selected() ? 0xFFFFFF : 0x808080);
        help.renderCentered(g, this.width / 2, y + 72, 10, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partial);
    }

    /** His face, small, beside the options. */
    private void drawFace(GuiGraphics g, int x, int y, int size) {
        g.blit(EyeQuads.STEVE, x, y, size, size, 8, 8, 8, 8, 64, 64);
        g.blit(EyeQuads.STEVE, x, y, size, size, 40, 8, 8, 8, 64, 64);
        float px = size / 8F;
        g.fill(x + (int) px, y + (int) (4 * px), x + (int) (3 * px), y + (int) (5 * px), 0xFFFFFFFF);
        g.fill(x + (int) (5 * px), y + (int) (4 * px), x + (int) (7 * px), y + (int) (5 * px), 0xFFFFFFFF);
    }

    /** Puts this screen in front of the vanilla "Create New World" screen, once per new world. */
    @Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
    public static final class Hook {
        static boolean passThrough;
        private static final Set<Screen> SHOWN = Collections.newSetFromMap(new WeakHashMap<>());

        @SubscribeEvent
        public static void onOpen(ScreenEvent.Opening event) {
            Screen s = event.getNewScreen();
            if (s instanceof SelectWorldScreen || s instanceof TitleScreen) {
                // world creation was cancelled: forget the choice
                ServerConfig.pendingMode = null;
                return;
            }
            if (!(s instanceof CreateWorldScreen create) || passThrough || SHOWN.contains(create)) return;
            SHOWN.add(create);
            event.setNewScreen(new WorldSetupScreen(event.getCurrentScreen(), create));
        }

        private Hook() {}
    }
}
