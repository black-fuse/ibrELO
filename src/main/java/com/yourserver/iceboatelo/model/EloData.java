package com.yourserver.iceboatelo.model;

import com.yourserver.iceboatelo.IceBoatElo;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class EloData {

    private final UUID uuid;
    private String name;
    private int elo;
    private int wins;
    private int losses;
    private int racesPlayed;

    public EloData(UUID uuid, String name, int elo, int wins, int losses, int racesPlayed) {
        this.uuid = uuid;
        this.name = name;
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
        this.racesPlayed = racesPlayed;
    }

    public UUID getUuid()           { return uuid; }
    public String getName()         { return name; }
    public void setName(String n)   { this.name = n; }
    public int getElo()             { return elo; }
    public void setElo(int e)       { this.elo = e; }
    public int getWins()            { return wins; }
    public void setWins(int w)      { this.wins = w; }
    public int getLosses()          { return losses; }
    public void setLosses(int l)    { this.losses = l; }
    public int getRacesPlayed()     { return racesPlayed; }
    public void setRacesPlayed(int r) { this.racesPlayed = r; }

    /** Returns the display name of the player's current rank. */
    public String getRankName() {
        return getRankInfo().name;
    }

    /** Returns the coloured rank label. */
    public String getRankDisplay() {
        return getRankInfo().display;
    }

    /** Returns the rank icon. */
    public String getRankIcon() {
        return getRankInfo().icon;
    }

    /**
     * Returns how many ELO points until the next rank, or -1 if at max rank.
     */
    public int getEloToNextRank() {
        List<RankEntry> entries = getSortedRanks();
        for (int i = 0; i < entries.size(); i++) {
            if (elo < entries.get(i).threshold) {
                return entries.get(i).threshold - elo;
            }
        }
        return -1; // max rank
    }

    /**
     * Returns the name of the next rank, or null if at max rank.
     */
    public String getNextRankName() {
        List<RankEntry> entries = getSortedRanks();
        for (RankEntry entry : entries) {
            if (elo < entry.threshold) {
                return entry.display;
            }
        }
        return null;
    }

    public double getWinRate() {
        if (racesPlayed == 0) return 0.0;
        return (double) wins / racesPlayed * 100.0;
    }

    // ─── Internal helpers ────────────────────────────────────────────────

    private RankInfo getRankInfo() {
        List<RankEntry> entries = getSortedRanks();
        RankEntry current = entries.get(0);
        for (RankEntry entry : entries) {
            if (elo >= entry.threshold) current = entry;
        }
        return new RankInfo(current.name, current.display, current.icon);
    }

    private List<RankEntry> getSortedRanks() {
        IceBoatElo plugin = IceBoatElo.getInstance();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks");

        // Built-in defaults if config is missing
        if (section == null) {
            return List.of(
                new RankEntry("Iron",        0,    "§7Iron",        "§7◈"),
                new RankEntry("Bronze",      500,  "§6Bronze",      "§6▲"),
                new RankEntry("Silver",      1000, "§fSilver",      "§f◆"),
                new RankEntry("Gold",        1500, "§e§lGold",      "§e★"),
                new RankEntry("Diamond",     2000, "§b§lDiamond",   "§b◇"),
                new RankEntry("Master",      2500, "§5§lMaster",    "§5⬡"),
                new RankEntry("Grandmaster", 3000, "§c§lGrandmaster","§c⚡")
            );
        }

        Map<String, String> colours = Map.of(
            "Iron",        "§7",
            "Bronze",      "§6",
            "Silver",      "§f",
            "Gold",        "§e§l",
            "Diamond",     "§b§l",
            "Master",      "§5§l",
            "Grandmaster", "§c§l"
        );
        Map<String, String> icons = Map.of(
            "Iron",        "§7◈",
            "Bronze",      "§6▲",
            "Silver",      "§f◆",
            "Gold",        "§e★",
            "Diamond",     "§b◇",
            "Master",      "§5⬡",
            "Grandmaster", "§c⚡"
        );

        List<RankEntry> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            int threshold = section.getInt(key);
            String colour = colours.getOrDefault(key, "§f");
            String icon   = icons.getOrDefault(key, "§f●");
            entries.add(new RankEntry(key, threshold, colour + key, icon));
        }
        entries.sort(Comparator.comparingInt(e -> e.threshold));
        return entries;
    }

    private record RankEntry(String name, int threshold, String display, String icon) {}
    private record RankInfo(String name, String display, String icon) {}
}
