package dev.zunserz.firstcontact.client;

import dev.zunserz.firstcontact.ContactNetwork;
import dev.zunserz.firstcontact.FirstContact;
import dev.zunserz.firstcontact.RadioSignals;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Retro radio receiver. Purely manual listening and transcription; no decode
 * button and no plaintext hidden in the GUI. All station availability is
 * server-authoritative and refreshed while the panel remains open.
 */
public final class RadioScreen extends Screen {
    private static final int W=344, H=202;
    private ContactNetwork.Snapshot state;
    private EditBox frequencyInput;
    private List<Tone> playing=List.of();
    private int elapsed=-1;
    private int ticks;
    private String hint="Введите частоту или настройте ручку.";
    private int previousStationId=-1;
    private boolean searchPending;

    private RadioScreen(ContactNetwork.Snapshot state) {
        super(Component.literal("FIRST CONTACT | RADIO RECEIVER"));
        this.state=state;
        previousStationId=state.stationId();
    }

    public static void receive(ContactNetwork.Snapshot packet) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof RadioScreen radio && radio.state.pos().equals(packet.pos())) {
            boolean stationChanged=radio.state.stationId()!=packet.stationId()
                ||radio.state.frequency()!=packet.frequency();
            radio.state=packet;
            if(stationChanged) {
                radio.elapsed=-1;
                radio.playing=List.of();
                radio.previousStationId=packet.stationId();
                radio.hint=packet.stationId()>0?"ПЕРЕДАЧА В ЭФИРЕ":"ПОМЕХИ / ПУСТОЙ КАНАЛ";
            }
            if(radio.searchPending) {
                radio.searchPending=false;
                radio.hint=packet.stationId()>0
                    ? "ПОИСК: АКТИВНАЯ ВОЛНА НАЙДЕНА"
                    : "ПОИСК: ПЕРЕДАЧИ ПОКА НЕ НАЙДЕНЫ";
            }
            if(radio.frequencyInput!=null && !radio.frequencyInput.isFocused())
                radio.frequencyInput.setValue(format(packet.frequency()));
        } else {
            mc.setScreen(new RadioScreen(packet));
        }
    }

    private static String format(int tenths) {
        return String.format(Locale.ROOT,"%.1f",tenths/10.0);
    }
    private static int nearestTenth(String raw) {
        try {
            double d=Double.parseDouble(raw.trim().replace(',','.'));
            if(!Double.isFinite(d) || d<RadioSignals.MIN/10.0
                    || d>RadioSignals.MAX/10.0)return -1;
            return (int)Math.round(d*10);
        } catch (NumberFormatException ex) {return -1;}
    }

    @Override protected void init() {
        int x=(width-W)/2,y=(height-H)/2;
        // Allow the player to type any precise decimal frequency including 98.7.
        frequencyInput=new EditBox(font,x+69,y+141,84,20,Component.literal("MHz"));
        frequencyInput.setMaxLength(6);
        frequencyInput.setFilter(s -> s.matches("[0-9.,]*"));
        frequencyInput.setValue(format(state.frequency()));
        addRenderableWidget(frequencyInput);

        addRenderableWidget(new BrassButton(x+17,y+141,47,20,
            Component.literal("−0.1"),()->tune(state.frequency()-1)));
        addRenderableWidget(new BrassButton(x+158,y+141,47,20,
            Component.literal("+0.1"),()->tune(state.frequency()+1)));
        addRenderableWidget(new BrassButton(x+210,y+141,52,20,
            Component.literal("98.7"),()->tune(RadioSignals.FIRST_CONTACT)));
        addRenderableWidget(new BrassButton(x+267,y+141,60,20,
            Component.literal("ВВЕСТИ"),this::applyEntry));

        addRenderableWidget(new BrassButton(x+17,y+167,129,21,
            Component.literal("ПРОСЛУШАТЬ"),this::play));
        addRenderableWidget(new BrassButton(x+152,y+167,52,21,
            Component.literal("КОПИЯ"),this::copyMorse));
        addRenderableWidget(new BrassButton(x+209,y+167,52,21,
            Component.literal("ПОИСК"),this::scan));
        addRenderableWidget(new BrassButton(x+266,y+167,61,21,
            Component.literal("КОСМОС"),()->tune(1400)));

        // Don't auto-focus input: shortcuts and normal movement of mouse work.

        // Do not seize keyboard focus unless the player clicks the field.
    }

    private void applyEntry() {
        int freq=nearestTenth(frequencyInput.getValue());
        if(freq<0){
            hint="НЕДОПУСТИМАЯ ЧАСТОТА: 50.0–180.0 MHz";
            return;
        }
        tune(freq);
        setFocused(null);
    }

    private void copyMorse() {
        if(state.morse().isEmpty()) {
            hint="НЕТ ЗАПИСИ ДЛЯ КОПИРОВАНИЯ";
            return;
        }
        // Only raw dots/dashes. No translation or coordinates in plaintext.
        Minecraft.getInstance().keyboardHandler.setClipboard(state.morse());
        hint="АЗБУКА МОРЗЕ СКОПИРОВАНА БЕЗ РАСШИФРОВКИ";
    }

    private void scan() {
        searchPending=true;
        elapsed=-1;
        playing=List.of();
        hint="СКАНИРОВАНИЕ ВОЛН...";
        PacketDistributor.sendToServer(new ContactNetwork.Action(state.pos(),"scan",0));
    }

    private void tune(int value) {
        int freq=Math.clamp(value,RadioSignals.MIN,RadioSignals.MAX);
        if(freq==state.frequency()) {
            if(frequencyInput!=null)frequencyInput.setValue(format(freq));
            return;
        }
        PacketDistributor.sendToServer(
            new ContactNetwork.Action(state.pos(),"tune",freq));
        state=new ContactNetwork.Snapshot(state.pos(),freq,0,0,"");
        if(frequencyInput!=null)frequencyInput.setValue(format(freq));
        elapsed=-1;
        playing=List.of();
        searchPending=false;
        hint="НАСТРОЙКА: "+format(freq)+" MHz";
    }

    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        // GLFW_KEY_ENTER=257, GLFW_KEY_KP_ENTER=335
        if((keyCode==257 || keyCode==335)
                &&frequencyInput!=null &&frequencyInput.isFocused()) {
            applyEntry();return true;
        }
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        int x=(width-W)/2,y=(height-H)/2;
        if(mx>=x && mx<x+W && my>=y && my<y+H) {
            tune(state.frequency()+(dy>0?1:-1));
            return true;
        }
        return super.mouseScrolled(mx,my,dx,dy);
    }

    private boolean hasSignal() {return state.stationId()>0;}

    private void play() {
        if(!hasSignal()) {
            // Always emit audio even on empty channels: a short test tone and
            // radio static, not a "broken" silent button.
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(FirstContact.STATIC.get(),1.0f,0.48f));
            playing=sequence("...",3);
            hint="СЛАБЫЙ ШУМ. ПРОВЕРКА ПРИЁМНИКА.";
        } else {
            RadioSignals.Station station=RadioSignals.byId(state.stationId());
            int speed=station==null?2:station.speed();
            playing=sequence(state.morse(),
                speed==1?5:(speed==3?2:3));
            hint=state.stationId()==RadioSignals.STORY_ID
                ? "АРХИВ / ЗАПИСЬ НЕИЗВЕСТНОГО ИСТОЧНИКА"
                : "ПРОСЛУШИВАНИЕ ПЕРЕХВАЧЕННОЙ ПЕРЕДАЧИ";
            if(state.signalStrength()<65)
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(FirstContact.STATIC.get(),1.0f,0.17f));
        }
        if(playing.isEmpty()){elapsed=-1;return;}
        elapsed=0;playCurrentTick();
    }

    private record Tone(int at,boolean dash) {}
    private static List<Tone> sequence(String morse,int unit) {
        ArrayList<Tone> result=new ArrayList<>();
        int time=0;
        for(int i=0;i<morse.length();i++) {
            char c=morse.charAt(i);
            if(c==' ') {time+=2*unit;continue;}
            if(c=='/') {time+=4*unit;continue;}
            if(c!='.' && c!='-')continue;
            result.add(new Tone(time,c=='-'));
            time+=(c=='-'?3*unit:unit)+unit;
        }
        return List.copyOf(result);
    }

    private void playCurrentTick() {
        for(Tone tone:playing) {
            if(tone.at()!=elapsed)continue;
            float volume=hasSignal()? Math.max(.28f,state.signalStrength()/100f):.50f;
            RadioSignals.Station station=RadioSignals.byId(state.stationId());
            float pitch=station==null?1.0f
                :station.speed()==1?.83f:(station.speed()==3?1.25f:1.0f);
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                    (tone.dash()?FirstContact.MORSE_DASH:FirstContact.MORSE_DOT).get(),
                    pitch,volume));
        }
    }

    @Override public void tick() {
        super.tick();
        ticks++;
        // A rare radio signal must be able to appear while the player watches
        // a tuned receiver, not just when re-opening the GUI.
        if(ticks%50==0)
            PacketDistributor.sendToServer(
                new ContactNetwork.Action(state.pos(),"poll",0));

        if(elapsed<0)return;
        elapsed++;
        playCurrentTick();
        Tone last=playing.isEmpty()?null:playing.getLast();
        if(last==null||elapsed>last.at()+16) {
            elapsed=-1;
            playing=List.of();
            hint=hasSignal()?"ПЕРЕДАЧА СОХРАНЕНА В ЭФИРЕ":"КАНАЛ ПУСТ";
        }
    }

    private static void plate(GuiGraphics g,int x,int y,int x2,int y2,int fill,int rim) {
        g.fill(x-1,y-1,x2+1,y2+1,0xFF100F0D);
        g.fill(x,y,x2,y2,fill);
        g.renderOutline(x,y,x2-x,y2-y,rim);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        // No Screen.renderBackground, no blur shader, no second super.render.
        g.fill(0,0,width,height,0xB9000000);
        int x=(width-W)/2,y=(height-H)/2;
        plate(g,x,y,x+W,y+H,0xFF2B1C18,0xFF9C733E);
        g.renderOutline(x+4,y+4,W-8,H-8,0xFF573726);
        g.renderOutline(x+8,y+8,W-16,H-16,0xFFD4AC63);
        plate(g,x+15,y+9,x+W-15,y+29,0xFF392923,0xFFC19A56);
        g.drawCenteredString(font,"ZUNSERZ  •  RADIO TELEGRAPH  •  MK-III",
            x+W/2,y+15,0xFFF3D69A);

        plate(g,x+17,y+34,x+W-17,y+87,0xFF10231E,0xFFB08A50);
        g.drawString(font,"FREQUENCY  /  50.0 - 180.0 MHz",x+25,y+39,0xFF8BC3AB,false);
        g.drawCenteredString(font,format(state.frequency())+" MHz",
            x+W/2,y+54,0xFFC4F4D6);
        g.fill(x+33,y+78,x+W-33,y+80,0xFF42594B);
        int markerX=x+33+(state.frequency()-RadioSignals.MIN)*(W-66)/
            (RadioSignals.MAX-RadioSignals.MIN);
        g.fill(markerX-1,y+72,markerX+2,y+84,0xFFF8C76E);
        g.drawString(font,"50",x+23,y+71,0xFF8AA999,false);
        g.drawString(font,"180",x+W-48,y+71,0xFF8AA999,false);

        plate(g,x+17,y+94,x+W-17,y+134,0xFF191917,0xFF715638);
        boolean signal=hasSignal();
        boolean plot=state.stationId()==RadioSignals.STORY_ID;
        int led=signal &&ticks%32<22 ? 0xFF75DB9C:0xFF6A614E;
        g.fill(x+25,y+101,x+29,y+105,led);
        RadioSignals.Station found=RadioSignals.byId(state.stationId());
        String classification=found==null?"НЕИЗВЕСТНЫЙ ИСТОЧНИК":switch(found.type()) {
            case "BEACON" -> "МАЯЧНЫЙ СИГНАЛ";
            case "BROADCAST" -> "РАДИОВЕЩАНИЕ";
            case "NUMBERS" -> "ЧИСЛОВАЯ СТАНЦИЯ";
            case "DISTRESS" -> "ТРЕВОЖНАЯ ПЕРЕДАЧА";
            case "TELEMETRY" -> "ТЕЛЕМЕТРИЯ";
            case "SATELLITE" -> "ОРБИТАЛЬНЫЙ СИГНАЛ";
            case "ANOMALY" -> "АНОМАЛЬНЫЙ СИГНАЛ";
            case "COORDINATES" -> "ЦИФРОВОЙ ПАКЕТ ДАННЫХ";
            default -> "НЕИЗВЕСТНЫЙ СИГНАЛ";
        };
        g.drawString(font,
            signal?(plot?"ЗАПИСЬ: НЕИЗВЕСТНЫЙ ИСТОЧНИК":classification)
                  :"ЭФИР: ПОМЕХИ / ОЖИДАНИЕ",
            x+36,y+99,signal?0xFFF3C987:0xFFAAA096,false);

        if(signal) {
            String raw=state.morse();
            // Raw dots and dashes are NOT deciphered; the player must listen
            // and solve the messages outside the game.
            String tape=font.plainSubstrByWidth(raw,W-60);
            g.drawCenteredString(font,tape,x+W/2,y+112,0xFFF7CD8C);
            g.drawCenteredString(font,"МОЩНОСТЬ "+state.signalStrength()
                +"%   •  ДЕШИФРАТОР ОТСУТСТВУЕТ",x+W/2,y+124,0xFFB8AD99);
        } else {
            g.drawCenteredString(font,"— — —  Т И Ш И Н А  — — —",x+W/2,y+115,0xFF81766D);
            g.drawCenteredString(font,"Попробуйте подождать или изменить частоту",
                x+W/2,y+126,0xFF9E958C);
        }

        g.drawString(font,"ШАГ 0.1  •  ENTER",x+18,y+191,0xFFD2B37A,false);
        String status=elapsed>=0?"► ИДЁТ ПЕРЕДАЧА":hint;
        g.drawString(font,font.plainSubstrByWidth(status,174),x+152,y+191,
            elapsed>=0?0xFFA8F0BD:0xFFE1B878,false);
        // Render controls exactly once, after the panel, without blur.
        for(var widget:renderables)widget.render(g,mouseX,mouseY,partial);
    }

    @Override public boolean isPauseScreen(){return false;}

    private final class BrassButton extends AbstractButton {
        private final Runnable action;
        BrassButton(int x,int y,int width,int height,Component title,Runnable action) {
            super(x,y,width,height,title);
            this.action=action;
        }
        @Override public void onPress(){action.run();}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial) {
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            int fill=isHoveredOrFocused()?0xFF735233:0xFF4D3527;
            plate(g,x,y,x+w,y+h,fill,isHoveredOrFocused()?0xFFFFD78D:0xFFC59D5A);
            g.drawCenteredString(font,getMessage(),x+w/2,y+(h-8)/2,
                isHoveredOrFocused()?0xFFFFE6A8:0xFFE3C28E);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }
}
