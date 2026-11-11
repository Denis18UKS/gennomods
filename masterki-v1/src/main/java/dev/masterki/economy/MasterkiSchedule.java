package dev.masterki.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.neoforged.fml.loading.FMLPaths;

/**
 * One server-wide real-world calendar day. Defaults to 05:00 Ufa time
 * (= 00:00 UTC). ZoneId handles DST if the operator selects another region.
 * Never derive the next renewal from the first player's login.
 */
public final class MasterkiSchedule {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_ZONE="Asia/Yekaterinburg";
    private static volatile ZoneId zone=ZoneId.of(DEFAULT_ZONE);
    private static volatile int resetHour=5;
    private static boolean initialized;
    private MasterkiSchedule(){}

    public static synchronized void reload(){
        Path path=FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("contract_schedule.json");
        try{
            Files.createDirectories(path.getParent());
            if(!Files.exists(path)){
                JsonObject defaults=new JsonObject();
                defaults.addProperty("timezone",DEFAULT_ZONE);
                defaults.addProperty("reset_hour",5);
                Files.writeString(path,JSON.toJson(defaults),StandardCharsets.UTF_8);
            }
            JsonObject cfg=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            String desiredZone=cfg.has("timezone")?cfg.get("timezone").getAsString():DEFAULT_ZONE;
            int desiredHour=cfg.has("reset_hour")?cfg.get("reset_hour").getAsInt():5;
            if(desiredHour<0 || desiredHour>23)throw new IllegalArgumentException("reset_hour must be 0..23");
            zone=ZoneId.of(desiredZone);
            resetHour=desiredHour;
        }catch(Exception ex){
            System.err.println("[Masterki] Invalid contract_schedule.json: "+ex.getMessage()+
                "; using Asia/Yekaterinburg 05:00");
            zone=ZoneId.of(DEFAULT_ZONE);
            resetHour=5;
        }
        initialized=true;
    }
    private static void init(){
        if(!initialized)reload();
    }
    public static ZoneId zone(){init();return zone;}
    public static int hour(){init();return resetHour;}

    /** Calendar-date key for the last 05:00 boundary, not the current local date. */
    public static long cycleDay(long millis){
        init();
        ZonedDateTime z=Instant.ofEpochMilli(millis).atZone(zone);
        LocalDate date=z.toLocalDate();
        if(z.toLocalTime().isBefore(LocalTime.of(resetHour,0)))date=date.minusDays(1);
        return date.toEpochDay();
    }
    public static long cutoff(long cycleDay,int daysAfter){
        init();
        return LocalDate.ofEpochDay(cycleDay).plusDays(daysAfter)
            .atTime(resetHour,0).atZone(zone).toInstant().toEpochMilli();
    }
    public static long nextCutoff(long now){
        return cutoff(cycleDay(now),1);
    }
    public static long cutoffAtOrAfter(long time,long now){
        long first=nextCutoff(now);
        if(time<=first)return first;
        return cutoff(cycleDay(now),2);
    }
}
