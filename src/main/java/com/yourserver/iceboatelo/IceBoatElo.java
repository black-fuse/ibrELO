package com.yourserver.iceboatelo;

import com.yourserver.iceboatelo.command.EloCommand;
import com.yourserver.iceboatelo.listener.RaceListener;
import com.yourserver.iceboatelo.manager.DatabaseManager;
import com.yourserver.iceboatelo.manager.EloManager;
import com.yourserver.iceboatelo.manager.QueueManager;
import com.yourserver.iceboatelo.manager.RaceManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public class IceBoatElo extends JavaPlugin {

    private static IceBoatElo instance;
    private DatabaseManager databaseManager;
    private EloManager eloManager;
    private QueueManager queueManager;
    @Getter
    private RaceManager raceManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("TimingSystem") == null) {
            getLogger().severe("TimingSystem not found! Disabling IceBoatElo.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);
        this.eloManager = new EloManager(this, databaseManager);
        this.queueManager = new QueueManager(this, eloManager);
        this.raceManager = new RaceManager(this, queueManager);

        getServer().getPluginManager().registerEvents(new RaceListener(this, eloManager, queueManager), this);

        EloCommand eloCommand = new EloCommand(this, eloManager, queueManager);
        getCommand("elo").setExecutor(eloCommand);
        getCommand("elo").setTabCompleter(eloCommand);

        getLogger().info("IceBoatElo enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        if (raceManager != null) raceManager.cleanupRaces();
    }

    public static IceBoatElo getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EloManager getEloManager() { return eloManager; }
    public QueueManager getQueueManager() { return queueManager; }
}
