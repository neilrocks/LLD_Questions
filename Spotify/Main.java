/*
=====================================================================
SPOTIFY / MUSIC STREAMING SERVICE - LOW LEVEL DESIGN (SDE2 INTERVIEW)
Single Java File Implementation
Time Target: ~35 minutes
=====================================================================

========================
FUNCTIONAL REQUIREMENTS
========================
1. User Management
   - Users can register
   - Users can login
   - Users can manage profile

2. Browse & Search
   - Users can browse songs, albums, artists
   - Users can search songs

3. Playlists
   - Users can create playlists
   - Users can add/remove songs from playlists
   - Users can manage playlists

4. Playback Controls
   - Play song
   - Pause song
   - Skip song
   - Seek in song

5. Recommendations
   - Recommend songs based on listening history

6. Follow Artists
   - Users can follow artists

========================
NON FUNCTIONAL
========================
Concurrency
Scalability
Extensibility

========================
CORE ENTITIES
========================

Song
 - id
 - title
 - artist
 - album
 - duration

Album
 - id
 - name
 - artist
 - list<Song>

Artist
 - id
 - name
 - albums
 - songs

User
 - id
 - username
 - password
 - playlists
 - listeningHistory
 - followedArtists

Playlist
 - id
 - name
 - songs

MusicLibrary (Singleton)
 - stores songs
 - stores albums
 - stores artists

UserManager (Singleton)
 - manages registration/login
 - manages users

MusicPlayer
 - play
 - pause
 - skip
 - seek

MusicRecommender (Singleton)
 - generates recommendations

MusicStreamingService
 - entry point
 - orchestrates system

=====================================================================
*/


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/* ================================================================
   SONG
   Represents a song in system
   ================================================================ */
class Song {

    private String id;
    private String title;
    private Artist artist;
    private Album album;
    private int duration; // seconds

    public Song(String id, String title, Artist artist, Album album, int duration) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Artist getArtist() { return artist; }
    public Album getAlbum() { return album; }
    public int getDuration() { return duration; }

    public String toString() {
        return title + " - " + artist.getName();
    }
}


/* ================================================================
   ALBUM
   Represents album containing multiple songs
   ================================================================ */
class Album {

    private String id;
    private String name;
    private Artist artist;
    private List<Song> songs = new ArrayList<>();

    public Album(String id, String name, Artist artist) {
        this.id = id;
        this.name = name;
        this.artist = artist;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public List<Song> getSongs() {
        return songs;
    }
}


/* ================================================================
   ARTIST
   Represents music artist
   ================================================================ */
class Artist {

    private String id;
    private String name;

    private List<Album> albums = new ArrayList<>();
    private List<Song> songs = new ArrayList<>();

    public Artist(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addAlbum(Album album) {
        albums.add(album);
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public String getName() {
        return name;
    }
}


/* ================================================================
   PLAYLIST
   Represents user playlist
   ================================================================ */
class Playlist {

    private String id;
    private String name;

    private List<Song> songs = new ArrayList<>();

    public Playlist(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public void removeSong(Song song) {
        songs.remove(song);
    }

    public List<Song> getSongs() {
        return songs;
    }
}


/* ================================================================
   USER
   Represents a spotify user
   ================================================================ */
class User {

    private String id;
    private String username;
    private String password;

    private List<Playlist> playlists = new ArrayList<>();
    private List<Song> listeningHistory = new ArrayList<>();
    private Set<Artist> followedArtists = new HashSet<>();

    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public boolean validatePassword(String pass) {
        return password.equals(pass);
    }

    public void addPlaylist(Playlist playlist) {
        playlists.add(playlist);
    }

    public List<Song> getListeningHistory() {
        return listeningHistory;
    }

    public void addToHistory(Song song) {
        listeningHistory.add(song);
    }

    public void followArtist(Artist artist) {
        followedArtists.add(artist);
    }
}


/* ================================================================
   MUSIC LIBRARY (SINGLETON)
   Central storage for songs/albums/artists
   ================================================================ */
class MusicLibrary {

    private static MusicLibrary instance = new MusicLibrary();

    private Map<String, Song> songs = new ConcurrentHashMap<>();
    private Map<String, Album> albums = new ConcurrentHashMap<>();
    private Map<String, Artist> artists = new ConcurrentHashMap<>();

    private MusicLibrary() {}

    public static MusicLibrary getInstance() {
        return instance;
    }

    public void addSong(Song song) {
        songs.put(song.getId(), song);
    }

    public void addAlbum(String id, Album album) {
        albums.put(id, album);
    }

    public void addArtist(String id, Artist artist) {
        artists.put(id, artist);
    }

    public Song getSong(String id) {
        return songs.get(id);
    }

    public List<Song> searchSong(String keyword) {

        List<Song> result = new ArrayList<>();

        for (Song s : songs.values()) {
            if (s.getTitle().toLowerCase().contains(keyword.toLowerCase())) {
                result.add(s);
            }
        }

        return result;
    }
}


/* ================================================================
   USER MANAGER (SINGLETON)
   Handles registration & login
   ================================================================ */
class UserManager {

    private static UserManager instance = new UserManager();

    private Map<String, User> users = new ConcurrentHashMap<>();

    private UserManager(){}

    public static UserManager getInstance() {
        return instance;
    }

    public void register(String id, String username, String password) {
        users.put(username, new User(id, username, password));
    }

    public User login(String username, String password) {

        User user = users.get(username);

        if(user != null && user.validatePassword(password))
            return user;

        return null;
    }
}


/* ================================================================
   MUSIC PLAYER
   Responsible for playback controls
   ================================================================ */
class MusicPlayer {

    private Song currentSong;
    private boolean isPlaying;
    private int currentPosition;

    public void play(Song song) {

        currentSong = song;
        isPlaying = true;
        currentPosition = 0;

        System.out.println("Playing: " + song);
    }

    public void pause() {

        isPlaying = false;
        System.out.println("Paused");
    }

    public void seek(int seconds) {

        currentPosition = seconds;
        System.out.println("Seek to: " + seconds);
    }

    public void skip(Song nextSong) {

        play(nextSong);
    }
}


/* ================================================================
   MUSIC RECOMMENDER (SINGLETON)
   Simple recommendation based on listening history
   ================================================================ */
class MusicRecommender {

    private static MusicRecommender instance = new MusicRecommender();

    private MusicRecommender(){}

    public static MusicRecommender getInstance() {
        return instance;
    }

    public List<Song> recommend(User user) {

        List<Song> history = user.getListeningHistory();

        if(history.isEmpty())
            return new ArrayList<>();

        Song lastSong = history.get(history.size()-1);

        // recommend songs from same artist
        Artist artist = lastSong.getArtist();

        return new ArrayList<>(artist.songs);
    }
}


/* ================================================================
   MUSIC STREAMING SERVICE
   Main entry point
   ================================================================ */
public class MusicStreamingService {

    public static void main(String[] args) {

        MusicLibrary library = MusicLibrary.getInstance();
        UserManager userManager = UserManager.getInstance();
        MusicPlayer player = new MusicPlayer();
        MusicRecommender recommender = MusicRecommender.getInstance();


        /* Create Sample Data */

        Artist arijit = new Artist("A1","Arijit Singh");

        Album album = new Album("AL1","Hits",arijit);

        Song s1 = new Song("S1","Tum Hi Ho",arijit,album,200);
        Song s2 = new Song("S2","Channa Mereya",arijit,album,220);

        album.addSong(s1);
        album.addSong(s2);

        arijit.addSong(s1);
        arijit.addSong(s2);

        library.addSong(s1);
        library.addSong(s2);


        /* User Registration */

        userManager.register("U1","swapnil","123");

        User user = userManager.login("swapnil","123");

        if(user == null){
            System.out.println("Login failed");
            return;
        }


        /* Search Song */

        List<Song> results = library.searchSong("Tum");

        Song songToPlay = results.get(0);


        /* Play Song */

        player.play(songToPlay);

        user.addToHistory(songToPlay);


        /* Recommendation */

        List<Song> rec = recommender.recommend(user);

        System.out.println("Recommended songs: " + rec);
    }
}