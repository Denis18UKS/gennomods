package dev.zunserz.firstcontact;

import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * Fictional in-world radio traffic. None of these broadcasts is a live real
 * station, an actual transmission from Earth, or a real extraterrestrial signal.
 * The broadcast windows are deterministic per world seed, station and game-time
 * window. All ambient stations have a chance BELOW 100%; the story archive at
 * 98.7 MHz is the only guaranteed transmission after story activation.
 *
 * Frequencies are stored as tenths of a MHz, so every 0.1 MHz is reachable.
 */
public final class RadioSignals {
    private RadioSignals() {}

    public static final int MIN = 500;      // 50.0 MHz
    public static final int MAX = 1800;     // 180.0 MHz
    public static final int FIRST_CONTACT = 987;
    public static final int STORY_ID = 99;
    public static final long WINDOW_TICKS = 1200L;

    /** origin only for source maintainers; deliberately NOT presented in game. */
    public record Station(int id, int frequency, int probability, int speed,
                          String region, String type, String morse) { }
    public record Reception(int stationId, int strength) {
        public static final Reception SILENCE = new Reception(0, 0);
    }

    private static Station station(int id, int freq, int chance, int speed,
                                   String region, String type, String message) {
        return new Station(id, freq, chance, speed, region, type, encode(message));
    }

    /** Simulated signals from various regions of Earth and fictional remote sources. */
    public static final List<Station> STATIONS = List.of(
        station( 1,  613, 78, 2, "North Atlantic",    "BEACON",    "CQ CQ NORTH"),
        station( 2,  705, 58, 3, "Western Europe",    "BROADCAST", "NIGHT SHIFT"),
        station( 3,  738, 46, 2, "Siberia",           "BEACON",    "SECTOR SEVEN"),
        station( 4,  886, 72, 3, "Central Europe",    "BEACON",    "DE ECHO"),
        station( 5,  914, 38, 2, "East Asia",         "NUMBERS",   "NINE FOUR TWO"),
        station( 6,  942, 23, 2, "Northern Africa",   "DISTRESS",  "NEED WATER"),
        station( 7,  961, 41, 3, "Southern America",  "BROADCAST", "HELLO AGAIN"),
        station( 8, 1013, 53, 2, "Pacific Ocean",     "BEACON",    "LIGHTHOUSE"),
        station( 9, 1048, 27, 2, "Arctic Circle",     "DISTRESS",  "LOST SIGNAL"),
        station(10, 1077, 18, 3, "Antarctica",        "TELEMETRY", "ICE NINE"),
        station(11, 1124, 36, 2, "Central Asia",      "BEACON",    "ALPHA TWO"),
        station(12, 1237, 21, 3, "Airborne relay",    "TELEMETRY", "MAINTAIN ALTITUDE"),
        station(13, 1375, 30, 3, "Earth orbit",       "SATELLITE", "ORBIT ECHO"),
        station(14, 1420, 12, 2, "Lunar echo",        "ANOMALY",   "DO NOT LAND"),
        station(15, 1516,  8, 2, "Deep-space relay",  "ANOMALY",   "WE SEE YOU"),
        station(16, 1682,  4, 1, "Unknown space",     "UNKNOWN",   "DO NOT FOLLOW"),
        station(17, 1749,  2, 1, "Uncharted source",  "UNKNOWN",   "THE GATE OPENS"),
        // Eight mysterious expedition coordinates. The actual X/Z values depend
        // on the current world's seed and are encoded into Morse on the SERVER.
        station(18,  524, 44, 2, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(19,  667, 31, 2, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(20,  807, 26, 1, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(21,  854, 18, 3, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(22,  974, 37, 2, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(23, 1131, 15, 1, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(24, 1314,  9, 2, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO"),
        station(25, 1619,  5, 1, "Unknown relay",    "COORDINATES", "X ZERO Z ZERO")
    );

    /**
     * Find the next station that actually has a signal in this time window.
     * Do not expose the complete frequency list in-game. Search wraps around.
     */
    public static int scan(ServerLevel level,int current,boolean storyReady) {
        int after=Integer.MAX_VALUE;
        int before=Integer.MAX_VALUE;
        for(Station station:STATIONS) {
            if(receive(level,station.frequency(),storyReady).stationId()==0)continue;
            if(station.frequency()>current)after=Math.min(after,station.frequency());
            before=Math.min(before,station.frequency());
        }
        if(storyReady) {
            if(FIRST_CONTACT>current)after=Math.min(after,FIRST_CONTACT);
            before=Math.min(before,FIRST_CONTACT);
        }
        if(after!=Integer.MAX_VALUE)return after;
        if(before!=Integer.MAX_VALUE)return before;
        return current;
    }

    public static Station byId(int id) {
        for (Station station : STATIONS) if (station.id() == id) return station;
        return null;
    }

    public static Station byFrequency(int frequency) {
        for (Station station : STATIONS)
            if (station.frequency() == frequency) return station;
        return null;
    }

    public static Reception receive(ServerLevel level, int frequency,
                                    boolean storyReady) {
        if (frequency == FIRST_CONTACT)
            return storyReady ? new Reception(STORY_ID, 100) : Reception.SILENCE;

        Station station = byFrequency(frequency);
        if (station == null) return Reception.SILENCE;

        long window = Math.floorDiv(level.getGameTime(), WINDOW_TICKS);
        long seed = level.getSeed()
            ^ ((long) station.id() * 0x9e3779b97f4a7c15L)
            ^ ((window + 1) * 0xbf58476d1ce4e5b9L);
        long roll = mix(seed);
        if (Long.remainderUnsigned(roll, 100) >= station.probability())
            return Reception.SILENCE;

        // Strength varies per received window; you can hear weak transmissions.
        int strength = 32 + (int) Long.remainderUnsigned(mix(roll), 69);
        return new Reception(station.id(), strength);
    }

    private static long mix(long v) {
        v ^= v >>> 30;
        v *= 0xbf58476d1ce4e5b9L;
        v ^= v >>> 27;
        v *= 0x94d049bb133111ebL;
        return v ^ (v >>> 31);
    }

    private static String encode(String message) {
        String[] letters = {
          ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..",
          ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.",
          "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."
        };
        String[] digits = {
          "-----", ".----", "..---", "...--", "....-", ".....",
          "-....", "--...", "---..", "----."
        };
        StringBuilder out = new StringBuilder();
        for (int i=0; i<message.length();i++) {
            char c=message.charAt(i);
            if (c == ' ') {
                if(!out.isEmpty()) out.append(" / ");
                continue;
            }
            if (!out.isEmpty() && out.charAt(out.length()-1)!=' ')
                out.append(' ');
            if (c >= 'A' && c <= 'Z') out.append(letters[c-'A']);
            else if (c >= '0' && c <= '9') out.append(digits[c-'0']);
        }
        return out.toString();
    }

    /** The server alone knows the coordinates. The client receives only CW. */
    public static String morseForId(int id, long worldSeed) {
        if (id == STORY_ID) return "... - --- .--.";
        if (ExpeditionSites.indexForStation(id) >= 0)
            return encode(ExpeditionSites.encodedClue(worldSeed,id));
        Station station = byId(id);
        return station == null ? "" : station.morse();
    }
}
