package com.yourserver.iceboatelo.manager;

import com.yourserver.iceboatelo.IceBoatElo;
import com.yourserver.iceboatelo.model.EloData;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.Objects;

public class EloManager {

    private final IceBoatElo plugin;
    private final DatabaseManager db;
    private final Map<UUID, EloData> cache = new HashMap<>();

    public EloManager(IceBoatElo plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public EloData getPlayer(UUID uuid) {
        if (cache.containsKey(uuid)) return cache.get(uuid);
        var player = Bukkit.getPlayer(uuid);
        String name = player != null ? player.getName() : "Unknown";
        EloData data = db.loadPlayer(uuid, name);
        cache.put(uuid, data);
        return data;
    }

    public void onPlayerJoin(UUID uuid, String name) {
        EloData data = db.loadPlayer(uuid, name);
        cache.put(uuid, data);
    }

    public void onPlayerQuit(UUID uuid) {
        EloData data = cache.remove(uuid);
        if (data != null) db.savePlayer(data);
    }

    /**
     * Process a ranked race result and update all players' ELO.
     * @param finishOrder List of UUIDs in finish order (index 0 = 1st place)
     */
    public void processRaceResult(List<UUID> finishOrder) {
        if (finishOrder.size() < 2) {
            plugin.getLogger().warning("Race had fewer than 2 players — skipping ELO update.");
            return;
        }

        int kFactor = plugin.getConfig().getInt("k-factor", 32);

        List<EloData> players = new ArrayList<>();
        for (UUID uuid : finishOrder) players.add(getPlayer(uuid));

        // Pairwise ELO: every player compared 1v1 against every other
        Map<UUID, Integer> changes = new HashMap<>();
        for (UUID uuid : finishOrder) changes.put(uuid, 0);

        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                EloData winner = players.get(i);
                EloData loser  = players.get(j);

                double expWinner = 1.0 / (1.0 + Math.pow(10, (loser.getElo() - winner.getElo()) / 400.0));
                double expLoser  = 1.0 - expWinner;

                int gainW = (int) Math.round(kFactor * (1.0 - expWinner));
                int gainL = (int) Math.round(kFactor * (0.0 - expLoser));

                changes.merge(winner.getUuid(), gainW, Integer::sum);
                changes.merge(loser.getUuid(),  gainL, Integer::sum);
            }
        }

        // Apply changes, enforce minimum ELO, update win/loss record
        int minElo = plugin.getConfig().getInt("minimum-elo", 0);
        for (int i = 0; i < players.size(); i++) {
            EloData data = players.get(i);
            int change = changes.getOrDefault(data.getUuid(), 0);
            data.setElo(Math.max(minElo, data.getElo() + change));
            data.setRacesPlayed(data.getRacesPlayed() + 1);
            if (i == 0) data.setWins(data.getWins() + 1);
            else        data.setLosses(data.getLosses() + 1);
            db.savePlayer(data);
        }

        broadcastResult(players, changes);
    }

    private void broadcastResult(List<EloData> players, Map<UUID, Integer> changes) {
        // Only send results to players who were in the race
        List<org.bukkit.entity.Player> racers = players.stream()
                .map(d -> Bukkit.getPlayer(d.getUuid()))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        java.util.function.Consumer<String> send = msg -> racers.forEach(p -> p.sendMessage(msg));

        send.accept("");
        send.accept("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        send.accept("§6§l        🏁 RACE RESULTS 🏁");
        send.accept("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String[] medals = {"§6§l1st 🥇", "§7§l2nd 🥈", "§c§l3rd 🥉"};
        for (int i = 0; i < players.size(); i++) {
            EloData data = players.get(i);
            int change = changes.getOrDefault(data.getUuid(), 0);
            String pos = i < medals.length ? medals[i] : "§f§l" + (i + 1) + "th";
            String ch  = change >= 0 ? "§a+" + change : "§c" + change;
            final String line = "  " + pos + " §f" + data.getName()
                    + "  §7" + data.getRankDisplay()
                    + "  §8|  §fELO: §e" + data.getElo()
                    + "  §8[" + ch + "§8]";
            send.accept(line);
        }
        send.accept("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        send.accept("");
    }

    public List<EloData> getLeaderboard(int limit) { return db.getTopPlayers(limit); }
    public int getPlayerRank(UUID uuid)             { return db.getPlayerRank(uuid); }
}
