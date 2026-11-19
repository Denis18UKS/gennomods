package dev.zunserz.firstcontact.client;

import dev.zunserz.firstcontact.ContactNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Retro brass-and-wood receiver. The first signal is stored on the server. */
public final class RadioScreen extends Screen {
    private static final int WIDTH = 322;
    private static final int HEIGHT = 200;
    private static final String MORSE = "... - --- .--."; // STOP
    private static final List<Pulse> PULSES = pulses();
    private ContactNetwork.Snapshot state;
    private boolean decoding;
    private int playback = -1;

    private record Pulse(int tick, boolean dash) { }

    private RadioScreen(ContactNetwork.Snapshot state) {
        super(Component.literal("FIRST CONTACT | RADIO RECEIVER"));
        this.state = state;
        this.decoding = state.messageRead();
    }

    public static void receive(ContactNetwork.Snapshot packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RadioScreen screen &&
                screen.state.pos().equals(packet.pos())) {
            screen.state = packet;
        } else {
            minecraft.setScreen(new RadioScreen(packet));
        }
    }

    @Override protected void init() {
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.literal("− 0.5"),
            button -> tune(-5)).bounds(x + 18, y + 148, 61, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+ 0.5"),
            button -> tune(5)).bounds(x + 85, y + 148, 61, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ПРОСЛУШАТЬ"),
            button -> startPlayback()).bounds(x + 152, y + 148, 151, 20).build());
        addRenderableWidget(Button.builder(Component.literal("РАСШИФРОВАТЬ"),
            button -> {
                if (hasSignal()) {
                    decoding = true;
                    PacketDistributor.sendToServer(new ContactNetwork.Action(
                        state.pos(), "read", state.frequency()));
                }
            }).bounds(x + 152, y + 174, 151, 18).build());
    }

    private boolean hasSignal() {
        return state.messageReady() && state.frequency() == 987;
    }

    private void tune(int delta) {
        int next = Math.clamp(state.frequency() + delta, 880, 1080);
        PacketDistributor.sendToServer(new ContactNetwork.Action(state.pos(), "tune", next));
        state = new ContactNetwork.Snapshot(state.pos(), state.messageReady(), state.messageRead(), next);
        decoding = false;
        playback = -1;
    }

    private void startPlayback() {
        if (!hasSignal()) return;
        playback = 0;
    }

    @Override public void tick() {
        super.tick();
        if (playback < 0) return;
        playback++;
        for (Pulse pulse : PULSES) {
            if (pulse.tick() == playback) {
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(),
                        pulse.dash() ? 0.60f : 1.12f, 0.5f));
            }
        }
        if (playback > PULSES.getLast().tick() + 20) playback = -1;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        // Solid-wood shell, brass bezel, engraved screws, luminous tuning dial.
        g.fill(x-3,y-3,x+WIDTH+3,y+HEIGHT+3,0xFF120F0C);
        g.fill(x,y,x+WIDTH,y+HEIGHT,0xFF49311E);
        g.renderOutline(x,y,WIDTH,HEIGHT,0xFFB9984B);
        g.fill(x+9,y+9,x+WIDTH-9,y+HEIGHT-9,0xFF332219);
        g.fill(x+13,y+13,x+WIDTH-13,y+135,0xFF92763F);
        g.renderOutline(x+13,y+13,WIDTH-26,122,0xFFDCC57A);
        g.fill(x+21,y+20,x+WIDTH-21,y+37,0xFF1A3028);
        g.drawCenteredString(font,"ZUNSERZ  •  RADIO TELEGRAPH  •  MK-I",x+WIDTH/2,y+24,0xFFE7D29A);
        g.fill(x+24,y+44,x+WIDTH-24,y+85,0xFF101C18);
        g.renderOutline(x+24,y+44,WIDTH-48,41,0xFFCFB66E);
        g.drawString(font,"FREQUENCY",x+32,y+51,0xFF8ACBAD,false);
        String frequency = String.format(Locale.ROOT, "%05.1f MHz",state.frequency()/10.0);
        g.drawCenteredString(font,frequency,x+WIDTH/2,y+63,0xFFD0FFCF);
        String indicator = hasSignal() ? "● SIGNAL STORED" : "○ NO TRANSMISSION";
        g.drawString(font,indicator,x+31,y+91,hasSignal()?0xFFACFFC6:0xFFAA967A,false);
        g.fill(x+24,y+107,x+WIDTH-24,y+131,0xFF1E1B15);
        if (hasSignal()) {
            g.drawCenteredString(font,MORSE,x+WIDTH/2,y+111,0xFFFFDB90);
            if (decoding) {
                g.drawCenteredString(font,"STOP / ОСТАНОВИТЕ ЗАПУСК",x+WIDTH/2,y+123,0xFFFFBCA4);
            } else {
                g.drawCenteredString(font,"ЗАПИСЬ ОЖИДАЕТ РАСШИФРОВКИ",x+WIDTH/2,y+123,0xFFBEB19B);
            }
        } else {
            g.drawCenteredString(font,"ШУМ ЭФИРА  •  ПОИСК ПЕРЕДАЧИ",x+WIDTH/2,y+116,0xFF91816D);
        }
        g.drawString(font,"Точное совпадение: 98.7 MHz",x+19,y+175,0xFFCAAC75,false);
        g.drawString(font,playback>=0?"ВОСПРОИЗВЕДЕНИЕ...":"АРХИВ / CHANNEL 01",x+19,y+188,0xFFAE956F,false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static List<Pulse> pulses() {
        ArrayList<Pulse> result = new ArrayList<>();
        int t = 0;
        for (int i = 0; i < MORSE.length(); i++) {
            char ch = MORSE.charAt(i);
            if (ch == ' ') { t += 8; continue; }
            result.add(new Pulse(t + 1, ch == '-'));
            t += (ch == '-' ? 12 : 4) + 4;
        }
        return List.copyOf(result);
    }

    @Override public boolean isPauseScreen() { return false; }
}
