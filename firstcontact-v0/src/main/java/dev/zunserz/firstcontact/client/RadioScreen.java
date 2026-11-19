package dev.zunserz.firstcontact.client;

import dev.zunserz.firstcontact.ContactNetwork;
import dev.zunserz.firstcontact.FirstContact;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pixel-precise Create-style brass radio GUI and server-backed message archive.
 * The test-sound button works BEFORE the story fires (no more silent dead button).
 * Dedicated original OGG assets distinguish Morse dot and dash durations.
 */
public final class RadioScreen extends Screen {
    private static final int W = 344, H = 230;
    private static final int SIGNAL_FREQ = 987;
    private static final String MORSE = "... - --- .--."; // STOP
    private static final List<Tone> MESSAGE = sequence(MORSE);
    private static final List<Tone> TEST = sequence("...");
    private ContactNetwork.Snapshot state;
    private boolean decoding;
    private List<Tone> active = List.of();
    private int elapsed = -1;
    private String hint = "";
    private int blinkTicks;
    private RadioScreen(ContactNetwork.Snapshot state) {
        super(Component.literal("RADIO TELEGRAPH | FIRST CONTACT"));
        this.state = state;
        this.decoding = state.messageRead();
    }

    public static void receive(ContactNetwork.Snapshot packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RadioScreen radio && radio.state.pos().equals(packet.pos())) {
            radio.state = packet;
            radio.decoding = radio.decoding || packet.messageRead();
        } else {
            mc.setScreen(new RadioScreen(packet));
        }
    }

    @Override protected void init() {
        int x = (width-W)/2, y=(height-H)/2;
        addRenderableWidget(new BrassButton(x+17,y+173,65,22,Component.literal("− 0.5"),()->tune(-5)));
        addRenderableWidget(new BrassButton(x+88,y+173,65,22,Component.literal("+ 0.5"),()->tune(5)));
        addRenderableWidget(new BrassButton(x+160,y+173,167,22,
            Component.literal("ПРОСЛУШАТЬ / ТЕСТ"),this::play));
        addRenderableWidget(new BrassButton(x+160,y+202,167,20,
            Component.literal("РАСШИФРОВАТЬ"),this::decode));
    }
    private boolean hasSignal() { return state.messageReady() && state.frequency() == SIGNAL_FREQ; }

    private void tune(int step) {
        int freq = Math.clamp(state.frequency()+step,880,1080);
        PacketDistributor.sendToServer(new ContactNetwork.Action(state.pos(),"tune",freq));
        state = new ContactNetwork.Snapshot(state.pos(),state.messageReady(),state.messageRead(),freq);
        hint = "Настроено: "+String.format(Locale.ROOT,"%.1f MHz",freq/10d);
        if (!hasSignal()) decoding = false;
        elapsed = -1;
    }

    private void play() {
        active = hasSignal() ? MESSAGE : TEST;
        elapsed = 0;
        hint = hasSignal()
            ? "ПРОСЛУШИВАНИЕ ЗАПИСИ 01  ·  АЗБУКА МОРЗЕ"
            : "ПРОВЕРКА ДИНАМИКА: 3 КОРОТКИХ СИГНАЛА";
        playCurrentTick();
    }

    private void decode() {
        if (!hasSignal()) {
            hint = "В АРХИВЕ НЕТ СИГНАЛА НА ЭТОЙ ЧАСТОТЕ";
            return;
        }
        decoding = true;
        hint = "ЗАПИСЬ ДЕШИФРОВАНА. ОРИГИНАЛ СОХРАНЁН.";
        PacketDistributor.sendToServer(new ContactNetwork.Action(state.pos(),"read",state.frequency()));
    }

    @Override public void tick() {
        super.tick();
        blinkTicks++;
        if(elapsed < 0) return;
        elapsed++;
        playCurrentTick();
        if(elapsed > MESSAGE.size()*32 + 10 || elapsed > end(active) + 12) {
            elapsed=-1;
            if (hasSignal()) hint = "ЗАПИСЬ ДОСТУПНА ДЛЯ ПОВТОРНОГО ПРОСЛУШИВАНИЯ";
        }
    }

    private void playCurrentTick() {
        if (elapsed < 0) return;
        for (Tone tone : active) {
            if (tone.at() != elapsed) continue;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                (tone.dash() ? FirstContact.MORSE_DASH : FirstContact.MORSE_DOT).get(),
                 1.0f, 0.85f));
        }
    }

    @Override public boolean mouseScrolled(double mx,double my,double deltaX,double deltaY) {
        int x = (width-W)/2, y=(height-H)/2;
        if (mx >= x && mx < x+W && my >= y && my < y+H) {
            tune(deltaY>0?5:-5); return true;
        }
        return super.mouseScrolled(mx,my,deltaX,deltaY);
    }

    private record Tone(int at,boolean dash) { }
    private static List<Tone> sequence(String code) {
        ArrayList<Tone> pulses=new ArrayList<>();
        int t=0;
        for (int i=0;i<code.length();i++) {
            char c=code.charAt(i);
            if(c==' ') { t+=6; continue; } // 3+6 = 9 tick inter-character gap
            pulses.add(new Tone(t,c=='-'));
            t += c=='-'?12:6; // 9 or 3 ticks of tone + 3 ticks symbol gap
        }
        return List.copyOf(pulses);
    }
    private static int end(List<Tone> a) {
        if(a.isEmpty())return 0;
        Tone last=a.getLast();
        return last.at()+(last.dash()?9:3);
    }

    private static void plate(GuiGraphics g,int x,int y,int x2,int y2,int fill,int rim) {
        g.fill(x-1,y-1,x2+1,y2+1,0xFF100F0D);
        g.fill(x,y,x2,y2,fill);
        g.renderOutline(x,y,x2-x,y2-y,rim);
    }

    @Override public void render(GuiGraphics g, int mouseX,int mouseY,float partial) {
        renderBackground(g,mouseX,mouseY,partial);
        int x=(width-W)/2,y=(height-H)/2;
        // Panel: thin multi-layer frames, high contrast pixel-art, non-blurred opaque fill.
        plate(g,x,y,x+W,y+H,0xFF2B1C18,0xFF9C733E);
        g.renderOutline(x+4,y+4,W-8,H-8,0xFF573726);
        g.renderOutline(x+8,y+8,W-16,H-16,0xFFD4AC63);
        plate(g,x+15,y+14,x+W-15,y+36,0xFF392923,0xFFC19A56);
        g.drawCenteredString(font,"ZUNSERZ  •  RADIO TELEGRAPH  •  MK-II",
            x+W/2,y+21,0xFFF3D69A);
        // Main tuning window: green phosphor with calibrated scale.
        plate(g,x+17,y+47,x+W-17,y+106,0xFF10231E,0xFFB08A50);
        g.drawString(font,"RECEIVER / FREQUENCY",x+26,y+53,0xFF8BC3AB,false);
        g.drawCenteredString(font,
            String.format(Locale.ROOT,"%05.1f MHz",state.frequency()/10.0),
            x+W/2,y+67,0xFFC4F4D6);
        g.fill(x+33,y+94,x+W-33,y+96,0xFF42594B);
        int markerX=x+33+(state.frequency()-880)*(W-66)/200;
        g.fill(markerX-1,y+87,markerX+2,y+99,0xFFF8C76E);
        g.drawString(font,"88",x+23,y+87,0xFF8AA999,false);
        g.drawString(font,"108",x+W-47,y+87,0xFF8AA999,false);
        // Message tape: the morse is shown only when the station actually received it.
        plate(g,x+17,y+115,x+W-17,y+162,0xFF191917,0xFF715638);
        boolean ready=hasSignal();
        int led=ready && blinkTicks%32<20 ? 0xFF75DB9C : 0xFF6A614E;
        g.fill(x+25,y+123,x+29,y+127,led);
        g.drawString(font,ready?"АРХИВ: НЕИЗВЕСТНАЯ ПЕРЕДАЧА":"ЭФИР: СИГНАЛ ОТСУТСТВУЕТ",
            x+36,y+121,ready?0xFFF3C987:0xFFAAA096,false);
        if (ready) {
            g.drawCenteredString(font,MORSE,x+W/2,y+135,0xFFF7CD8C);
            g.drawCenteredString(font,decoding?"STOP  /  ОСТАНОВИТЕ ЗАПУСК"
                : "ЗАПИСЬ 01  •  НЕ РАСШИФРОВАНА",x+W/2,y+148,
                decoding?0xFFEEB19B:0xFFBBBAA7);
        } else {
            g.drawCenteredString(font,"— — —   Ш У М   Э Ф И Р А   — — —",x+W/2,y+143,
                0xFF81766D);
        }
        g.drawString(font,"ШАГ НАСТРОЙКИ: 0.5 MHz",x+18,y+201,0xFFB39465,false);
        g.drawString(font,elapsed>=0?"► ИДЁТ ВОСПРОИЗВЕДЕНИЕ":"● "+hint,
            x+17,y+216,elapsed>=0?0xFFA8F0BD:0xFFE1B878,false);
        super.render(g,mouseX,mouseY,partial);
    }

    @Override public boolean isPauseScreen() { return false; }

    private final class BrassButton extends AbstractButton {
        private final Runnable onClick;
        BrassButton(int x,int y,int w,int h,Component title,Runnable action) {
            super(x,y,w,h,title);
            onClick=action;
        }
        @Override public void onPress() { onClick.run(); }
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float delta) {
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            int bg=isHoveredOrFocused()?0xFF735233:0xFF4D3527;
            plate(g,x,y,x+w,y+h,bg,isHoveredOrFocused()?0xFFFFD78D:0xFFC59D5A);
            g.drawCenteredString(font,getMessage(),x+w/2,y+(h-8)/2,
                isHoveredOrFocused()?0xFFFFE6A8:0xFFE3C28E);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }
}
