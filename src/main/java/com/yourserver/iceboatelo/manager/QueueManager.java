package com.yourserver.iceboatelo.manager;

import com.yourserver.iceboatelo.IceBoatElo;
import com.yourserver.iceboatelo.model.EloData;
import lombok.Getter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class QueueManager {

    private final IceBoatElo plugin;
    @Getter
    private final EloManager eloManager;

    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private BukkitTask countdownTask = null;
    private int secondsRemaining = 0;

    private boolean rankedRaceActive = false;
    private List<UUID> currentRankedPlayers = new ArrayList<>();

    private String lastTrack = null;

    public QueueManager(IceBoatElo plugin, EloManager eloManager) {
        this.plugin = plugin;
        this.eloManager = eloManager;
    }

    // ─── Queue ───────────────────────────────────────────────────────────

    public boolean joinQueue(Player player) {
        if (queue.contains(player.getUniqueId())) return false;
        if (rankedRaceActive) {
            player.sendMessage("§8[§6Ranked§8] §cA race is already in progress. Wait for it to finish.");
            return false;
        }
        queue.add(player.getUniqueId());
        broadcast("§a" + player.getName() + " §fjoined the queue. §8(" + queue.size() + " queued)");

        if (queue.size() == 1) {
            startCountdown(plugin.getConfig().getInt("queue-countdown", 120));
        }
        return true;
    }

    public boolean leaveQueue(Player player) {
        boolean removed = queue.remove(player.getUniqueId());
        if (!removed) return false;
        broadcast("§c" + player.getName() + " §fleft the queue. §8(" + queue.size() + " queued)");
        if (queue.isEmpty()) cancelCountdown();
        return true;
    }

    public boolean isInQueue(UUID uuid)       { return queue.contains(uuid); }
    public int getQueueSize()                 { return queue.size(); }
    public int getSecondsRemaining()          { return secondsRemaining; }
    public boolean isRankedRaceActive()       { return rankedRaceActive; }
    public boolean isInRankedHeat(UUID uuid)  { return rankedRaceActive && currentRankedPlayers.contains(uuid); }

    public void onRaceFinished() {
        rankedRaceActive = false;
        currentRankedPlayers.clear();
        broadcast("§6Ranked race finished! Type §a/elo queue §6to join the next one.");
    }

    // ─── Countdown ───────────────────────────────────────────────────────

    private void startCountdown(int seconds) {
        secondsRemaining = seconds;
        broadcast("§6§l⚡ Ranked race §fstarting in §e" + formatTime(secondsRemaining)
                + "§f! Type §a/elo queue §fto join.");

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                secondsRemaining--;
                if (shouldAnnounce(secondsRemaining)) {
                    broadcast("§6§l⚡ Ranked race §fstarting in §e" + formatTime(secondsRemaining)
                            + "§f! §8(" + queue.size() + " queued)");
                }
                if (secondsRemaining <= 0) {
                    cancel();
                    countdownTask = null;
                    launchRace();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        secondsRemaining = 0;
        broadcast("§cQueue cancelled — no players remaining.");
    }

    private boolean shouldAnnounce(int s) {
        return s == 60 || s == 30 || s == 10 || s == 5;
    }

    // ─── Launch ──────────────────────────────────────────────────────────

    private void launchRace() {
        if (queue.isEmpty()) {
            broadcast("§cNo players queued — race cancelled.");
            return;
        }

        List<UUID> players = new ArrayList<>(queue);
        queue.clear();

        String trackName = pickTrack();
        if (trackName == null) {
            broadcast("§cNo ranked tracks configured! Add tracks to §eranked-tracks §cin config.yml.");
            return;
        }

        Optional<Track> trackOpt = getTrackByName(trackName);
        if (trackOpt.isEmpty()) {
            broadcast("§cTrack '§e" + trackName + "§c' not found in TimingSystem! Check the name in config.yml.");
            return;
        }

        // Need at least one online player to act as host
        Player host = players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (host == null) {
            broadcast("§cAll queued players went offline — race cancelled.");
            return;
        }

        int laps = plugin.getConfig().getInt("race-laps", 5);
        int pits = plugin.getConfig().getInt("race-pits", 0);

        // Add all queued players
        int added = 0;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) added++;
        }

        if (added < 2) {
            broadcast("§cNot enough players online to race — cancelling.");
            return;
        }

        // Announce
        broadcast("");
        broadcast("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        broadcast("§6§l   ⚡ RANKED RACE STARTING ⚡");
        broadcast("§7  Track: §e" + trackName + "  §7| Laps: §e" + laps + "  §7| Pits: §e" + pits);

        StringBuilder names = new StringBuilder("§7  Racers: ");
        for (int i = 0; i < players.size(); i++) {
            Player p = Bukkit.getPlayer(players.get(i));
            EloData data = eloManager.getPlayer(players.get(i));
            names.append("§f").append(p != null ? p.getName() : "?")
                 .append(" §8(").append(data.getElo()).append(")");
            if (i < players.size() - 1) names.append("§8, ");
        }
        broadcast(names.toString());
        broadcast("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        currentRankedPlayers = new ArrayList<>(players);
        rankedRaceActive = true;


        boolean raceCreated = plugin.getRaceManager().startRankedRace(
                host,
                trackOpt.get(),
                laps,
                pits,
                players
        );

        if (!raceCreated) {
            broadcast("§cFailed to create ranked race.");
            return;
        }

    }

    // for testing purposes
    public void forceStart() {
        if (queue.isEmpty()) {
            return;
        }

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        secondsRemaining = 0;
        launchRace();
    }

    // ─── Track pool ──────────────────────────────────────────────────────

    private String pickTrack() {
        List<String> tracks = plugin.getConfig().getStringList("ranked-tracks");
        if (tracks.isEmpty()) return null;
        List<String> available = new ArrayList<>(tracks);
        if (available.size() > 1 && lastTrack != null) available.remove(lastTrack);
        String chosen = available.get(new Random().nextInt(available.size()));
        lastTrack = chosen;
        return chosen;
    }

    private Optional<Track> getTrackByName(String name) {
        return TrackDatabase.getTrack(name);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void broadcast(String msg) {
        Bukkit.broadcastMessage("§8[§6Ranked§8] §r" + msg);
    }

    private String formatTime(int secs) {
        if (secs >= 60) { int m = secs / 60, s = secs % 60; return m + ":" + String.format("%02d", s); }
        return secs + "s";
    }
}
