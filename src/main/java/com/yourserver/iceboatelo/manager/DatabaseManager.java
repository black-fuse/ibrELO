package com.yourserver.iceboatelo.manager;

import com.yourserver.iceboatelo.IceBoatElo;
import com.yourserver.iceboatelo.model.EloData;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {
    private final IceBoatElo plugin;
    private Connection connection;

    public DatabaseManager(IceBoatElo plugin) {
        this.plugin = plugin;
        openConnection();
        createTable();
    }

    private void openConnection() {
        try {
            File df = plugin.getDataFolder();
            if (!df.exists()) df.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + df.getAbsolutePath() + "/elo.db");
            plugin.getLogger().info("Connected to SQLite database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite!", e);
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS elo_data (" +
                "uuid TEXT PRIMARY KEY, name TEXT NOT NULL, elo INTEGER NOT NULL DEFAULT 1000," +
                "wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0," +
                "races_played INTEGER NOT NULL DEFAULT 0);";
        try (Statement s = connection.createStatement()) { s.execute(sql); }
        catch (SQLException e) { plugin.getLogger().log(Level.SEVERE, "Could not create table!", e); }
    }

    public EloData loadPlayer(UUID uuid, String name) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM elo_data WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                EloData d = new EloData(uuid, rs.getString("name"), rs.getInt("elo"),
                        rs.getInt("wins"), rs.getInt("losses"), rs.getInt("races_played"));
                if (!d.getName().equals(name)) { d.setName(name); savePlayer(d); }
                return d;
            }
        } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Failed to load " + uuid, e); }
        int startElo = plugin.getConfig().getInt("starting-elo", 1000);
        EloData nd = new EloData(uuid, name, startElo, 0, 0, 0);
        savePlayer(nd);
        return nd;
    }

    public void savePlayer(EloData d) {
        String sql = "INSERT INTO elo_data (uuid,name,elo,wins,losses,races_played) VALUES(?,?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,elo=excluded.elo," +
                "wins=excluded.wins,losses=excluded.losses,races_played=excluded.races_played;";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,d.getUuid().toString()); ps.setString(2,d.getName());
            ps.setInt(3,d.getElo()); ps.setInt(4,d.getWins());
            ps.setInt(5,d.getLosses()); ps.setInt(6,d.getRacesPlayed());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Failed to save " + d.getUuid(), e); }
    }

    public List<EloData> getTopPlayers(int limit) {
        List<EloData> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM elo_data ORDER BY elo DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new EloData(UUID.fromString(rs.getString("uuid")),
                    rs.getString("name"), rs.getInt("elo"),
                    rs.getInt("wins"), rs.getInt("losses"), rs.getInt("races_played")));
        } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Failed to fetch leaderboard.", e); }
        return list;
    }

    public int getPlayerRank(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM elo_data WHERE elo>(SELECT elo FROM elo_data WHERE uuid=?)")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) + 1;
        } catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Failed to get rank.", e); }
        return -1;
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { plugin.getLogger().log(Level.WARNING, "Error closing DB.", e); }
    }
}
