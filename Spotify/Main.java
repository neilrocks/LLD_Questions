package Spotify;

import java.util.*;

/* ───────────────────────── SONG ───────────────────────── */

class Song {
    private String title;
    private String artist;
    private int duration;
    private String path;

    public Song(String title, String artist, int duration, String path) {
        this.title = title;
        this.artist = artist;
        this.duration = duration;
        this.path = path;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public int getDuration() {
        return duration;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "Song{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", duration=" + duration +
                ", path='" + path + '\'' +
                '}';
    }
}

/* ──────────────── AUDIO‑OUTPUT DEVICES & EXTERNAL APIS ──────────────── */

interface AudioOutputDevice {
    void playAudio(Song song);
}

class BluetoothExternalApi {
    void play(Song s) {
        System.out.println("Playing on Bluetooth speaker: " + s.getTitle());
    }
}

class HeadphoneExternalApi {
    void play(Song s) {
        System.out.println("Playing on headphone: " + s.getTitle());
    }
}

class EarphoneExternalApi {
    void play(Song s) {
        System.out.println("Playing on earphone: " + s.getTitle());
    }
}

class BluetoothSpeaker implements AudioOutputDevice {
    private final BluetoothExternalApi api;

    BluetoothSpeaker(BluetoothExternalApi api) {
        this.api = api;
    }

    public void playAudio(Song s) {
        api.play(s);
    }
}

class Headphone implements AudioOutputDevice {
    private final HeadphoneExternalApi api;

    Headphone(HeadphoneExternalApi api) {
        this.api = api;
    }

    public void playAudio(Song s) {
        api.play(s);
    }
}

class Earphone implements AudioOutputDevice {
    private final EarphoneExternalApi api;

    Earphone(EarphoneExternalApi api) {
        this.api = api;
    }

    public void playAudio(Song s) {
        api.play(s);
    }
}

/* ───────────────────── DEVICE FACTORY & MANAGER ───────────────────── */

enum DeviceType {
    BLUETOOTH_SPEAKER, HEADPHONE, EARPHONE
}

class DeviceFactory {
    static AudioOutputDevice getDevice(DeviceType t) {
        switch (t) {
            case BLUETOOTH_SPEAKER:
                return new BluetoothSpeaker(new BluetoothExternalApi());
            case HEADPHONE:
                return new Headphone(new HeadphoneExternalApi());
            case EARPHONE:
                return new Earphone(new EarphoneExternalApi());
            default:
                throw new IllegalArgumentException("Unknown device " + t);
        }
    }
}

class DeviceManager {
    private static DeviceManager instance;
    private DeviceType currentType;

    private DeviceManager() {
    }

    public static DeviceManager getInstance() {
        if (instance == null)
            instance = new DeviceManager();
        return instance;
    }

    public void connect(DeviceType t) {
        currentType = t;
        switch (t) {
            case BLUETOOTH_SPEAKER:
                System.out.println("Connecting to Bluetooth Speaker...");
                break;
            case HEADPHONE:
                System.out.println("Connecting to Headphone...");
                break;
            case EARPHONE:
                System.out.println("Connecting to Earphone...");
                break;
        }
    }

    public AudioOutputDevice getDevice() {
        return DeviceFactory.getDevice(currentType);
    }
}

/* ───────────────────────── PLAYLIST DOMAIN ───────────────────────── */

class Playlist {
    private final String name;
    private final List<Song> songs = new ArrayList<>();

    Playlist(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Song> getSongs() {
        return songs;
    }
}

class PlaylistManager {
    private static PlaylistManager instance;
    private final List<Playlist> playlists = new ArrayList<>();
    private Playlist current;

    private PlaylistManager() {
    }

    public static PlaylistManager getInstance() {
        if (instance == null)
            instance = new PlaylistManager();
        return instance;
    }

    public void createPlaylist(String name) {
        Playlist p = new Playlist(name);
        playlists.add(p);
        if (current == null)
            current = p;
    }

    public void addPlaylist(Playlist p) {
        playlists.add(p);
    }

    public void setCurrentPlaylist(Playlist p) {
        current = p;
    }

    public Playlist getCurrentPlaylist() {
        return current;
    }

    public List<Playlist> getPlaylists() {
        return playlists;
    }
}

/* ───────────────────────── PLAYING STRATEGIES ───────────────────────── */

enum StrategyType {
    SEQUENTIAL, RANDOM, CUSTOM
}

abstract class PlayingStrategy {
    protected List<Song> songs;
    protected Playlist playlist;

    public abstract boolean hasNext();

    public abstract Song next();

    public abstract boolean hasPrevious();

    public abstract Song previous();
}

/* ―― Sequential ―― */
class SequentialPlayingStrategy extends PlayingStrategy {
    private int idx;

    SequentialPlayingStrategy(Playlist p) {
        setPlaylist(p);
    }

    void setPlaylist(Playlist p) {
        playlist = p;
        songs = p.getSongs();
        idx = 0;
    }

    public boolean hasNext() {
        return idx < songs.size();
    }

    public Song next() {
        return hasNext() ? songs.get(idx++) : null;
    }

    public boolean hasPrevious() {
        return idx > 0;
    }

    public Song previous() {
        return hasPrevious() ? songs.get(--idx) : null;
    }
}

/* ―― Random ―― */
class RandomPlayingStrategy extends PlayingStrategy {
    private final Random rng = new Random();
    private final Set<Integer> played = new HashSet<>();

    RandomPlayingStrategy(Playlist p) {
        setPlaylist(p);
    }

    void setPlaylist(Playlist p) {
        playlist = p;
        songs = p.getSongs();
        played.clear();
    }

    public boolean hasNext() {
        return played.size() < songs.size();
    }

    public Song next() {
        if (!hasNext())
            return null;
        int i;
        do {
            i = rng.nextInt(songs.size());
        } while (played.contains(i));
        played.add(i);
        return songs.get(i);
    }

    public boolean hasPrevious() {
        return !played.isEmpty();
    }

    public Song previous() {
        if (!hasPrevious())
            return null;
        int i = played.iterator().next();
        played.remove(i);
        return songs.get(i);
    }
}

/* ―― Custom ―― */
class CustomPlayingStrategy extends PlayingStrategy {
    private List<Song> custom;
    private int idx;

    CustomPlayingStrategy(Playlist p, List<Song> order) {
        setCustomOrder(p, order);
    }

    void setCustomOrder(Playlist p, List<Song> order) {
        playlist = p;
        songs = p.getSongs();
        custom = order;
        idx = 0;
    }

    public boolean hasNext() {
        return idx < custom.size();
    }

    public Song next() {
        return hasNext() ? custom.get(idx++) : null;
    }

    public boolean hasPrevious() {
        return idx > 0;
    }

    public Song previous() {
        return hasPrevious() ? custom.get(--idx) : null;
    }

    public void addToNext(Song s) {
        if (idx < custom.size())
            custom.add(idx, s);
        else
            custom.add(s);
    }
}

/* ───────────────────────── STRATEGY MANAGER ───────────────────────── */

class StrategyManger {
    private static StrategyManger instance;

    private final Map<StrategyType, PlayingStrategy> map = new EnumMap<>(StrategyType.class);
    private PlayingStrategy current;

    private StrategyManger(Playlist defaultPl) {
        map.put(StrategyType.SEQUENTIAL, new SequentialPlayingStrategy(defaultPl));
        map.put(StrategyType.RANDOM, new RandomPlayingStrategy(defaultPl));
        map.put(StrategyType.CUSTOM, new CustomPlayingStrategy(defaultPl, new ArrayList<>()));
        current = map.get(StrategyType.SEQUENTIAL);
    }

    /* Singleton accessor */
    public static StrategyManger getInstance() {
        if (instance == null) {
            Playlist pl = PlaylistManager.getInstance().getCurrentPlaylist();
            if (pl == null)
                pl = new Playlist("Default"); // safe‑guard
            instance = new StrategyManger(pl);
        }
        return instance;
    }

    /*
     * ←―― NEW LOGIC: make sure *every* strategy sees the latest playlist before use
     * ――→
     */
    private void refreshStrategiesWithCurrentPlaylist() {
        Playlist pl = PlaylistManager.getInstance().getCurrentPlaylist();
        ((SequentialPlayingStrategy) map.get(StrategyType.SEQUENTIAL)).setPlaylist(pl);
        ((RandomPlayingStrategy) map.get(StrategyType.RANDOM)).setPlaylist(pl);
        ((CustomPlayingStrategy) map.get(StrategyType.CUSTOM)).setCustomOrder(pl, new ArrayList<>(pl.getSongs()));
    }

    public void setStrategy(StrategyType t) {
        refreshStrategiesWithCurrentPlaylist();
        current = map.get(t);
    }

    public PlayingStrategy getCurrentStrategy() {
        return current;
    }
}

/* ───────────────────────── AUDIO ENGINE ───────────────────────── */

class AudioEngine {
    private static AudioEngine instance;
    private Song current;

    private AudioEngine() {
    }

    public static AudioEngine getInstance() {
        if (instance == null)
            instance = new AudioEngine();
        return instance;
    }

    public void playSong(AudioOutputDevice dev, Song s) {
        current = s;
        if (dev != null)
            dev.playAudio(s);
        else
            System.out.println("No audio device.");
    }

    public void pauseSong() {
        if (current != null)
            System.out.println("Pausing: " + current.getTitle());
        else
            System.out.println("Nothing playing.");
    }
}

/* ───────────────────────── FACADE ───────────────────────── */

class MusicPlayerFacade {
    private static MusicPlayerFacade instance;
    private final AudioEngine audio = AudioEngine.getInstance();
    private final PlaylistManager plMgr = PlaylistManager.getInstance();
    private final StrategyManger strat = StrategyManger.getInstance();
    private final DeviceManager devMgr = DeviceManager.getInstance();

    private MusicPlayerFacade() {
    }

    public static MusicPlayerFacade getInstance() {
        if (instance == null)
            instance = new MusicPlayerFacade();
        return instance;
    }

    /* Thin‑wrappers */
    public void connectDevice(DeviceType t) {
        devMgr.connect(t);
    }

    public void playSong(Song s) {
        audio.playSong(devMgr.getDevice(), s);
    }

    public void pauseSong() {
        audio.pauseSong();
    }

    public void setStrategy(StrategyType t) {
        strat.setStrategy(t);
    }

    public PlayingStrategy getCurrentStrategy() {
        return strat.getCurrentStrategy();
    }

    public void createPlaylist(String name) {
        plMgr.createPlaylist(name);
    }

    public void addPlaylist(Playlist p) {
        plMgr.addPlaylist(p);
    }

    public Playlist getCurrentPlaylist() {
        return plMgr.getCurrentPlaylist();
    }
}

/* ───────────────────────── APPLICATION ───────────────────────── */

class MusicPlayerApplication {
    private static MusicPlayerApplication instance;
    private final MusicPlayerFacade mp = MusicPlayerFacade.getInstance();

    private MusicPlayerApplication() {
    }

    public static MusicPlayerApplication getInstance() {
        if (instance == null)
            instance = new MusicPlayerApplication();
        return instance;
    }

    public void run() {
        /* 1. Build playlist & songs */
        mp.createPlaylist("My Favourite Songs");
        Playlist pl = mp.getCurrentPlaylist();
        Song s1 = new Song("Shape of You", "Ed Sheeran", 240, "/music/shape_of_you.mp3");
        Song s2 = new Song("Blinding Lights", "The Weeknd", 200, "/music/blinding_lights.mp3");
        Song s3 = new Song("Levitating", "Dua Lipa", 220, "/music/levitating.mp3");
        Song s4 = new Song("Bad Guy", "Billie Eilish", 180, "/music/bad_guy.mp3");
        Collections.addAll(pl.getSongs(), s1, s2, s3, s4);

        /* 2. Connect device & play first song manually */
        mp.connectDevice(DeviceType.BLUETOOTH_SPEAKER);
        mp.playSong(s1);

        /* 3. Sequential strategy */
        mp.setStrategy(StrategyType.SEQUENTIAL);
        PlayingStrategy strat = mp.getCurrentStrategy();
        while (strat.hasNext())
            mp.playSong(strat.next());

        /* 4. Random strategy */
        mp.setStrategy(StrategyType.RANDOM);
        strat = mp.getCurrentStrategy();
        while (strat.hasNext())
            mp.playSong(strat.next());

        /* 5. Switch device */
        mp.connectDevice(DeviceType.HEADPHONE);
        mp.playSong(s2);
    }
}

/* ───────────────────────── MAIN ───────────────────────── */

public class Main {
    public static void main(String[] args) {
        MusicPlayerApplication.getInstance().run();
    }
}
