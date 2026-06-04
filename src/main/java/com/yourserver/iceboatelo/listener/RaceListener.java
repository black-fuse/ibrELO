package com.yourserver.iceboatelo.listener;

import com.yourserver.iceboatelo.IceBoatElo;
import com.yourserver.iceboatelo.manager.EloManager;
import com.yourserver.iceboatelo.manager.QueueManager;
import com.yourserver.iceboatelo.manager.RaceManager;
import me.makkuusen.timing.system.api.events.HeatFinishEvent;
import me.makkuusen.timing.system.api.events.driver.DriverFinishHeatEvent;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Driver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class RaceListener implements Listener {

    private final IceBoatElo plugin;
    private final EloManager eloManager;
    private final QueueManager queueManager;

    private Map<Heat, Driver> firstFinishers = new HashMap<>();

    public RaceListener(IceBoatElo plugin, EloManager eloManager, QueueManager queueManager) {
        this.plugin = plugin;
        this.eloManager = eloManager;
        this.queueManager = queueManager;
    }

    /**
     * Fires when any heat finishes. We check if it's our ranked QuickRace heat
     * by comparing the heat ID to the one managed by QuickRaceAPI.
     */
    @EventHandler
    public void onHeatFinish(HeatFinishEvent e) {
        Heat heat = e.getHeat();

        // Ignore if no ranked race is active
        if (!queueManager.isRankedRaceActive()) return;

        // Check this is the QuickRace heat we started
        if (!plugin.getRaceManager().getActiveRankedHeats().contains(heat)) {
            return;
        }

        // getLivePositions() is already sorted 1st → last by updatePositions()
        // which is called inside finishHeat() before HeatFinishEvent fires.
        List<UUID> finishOrder = heat.getLivePositions().stream()
                .map(driver -> driver.getTPlayer().getUniqueId())
                .collect(Collectors.toList());

        eloManager.processRaceResult(finishOrder);
        queueManager.onRaceFinished();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        eloManager.onPlayerJoin(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (queueManager.isInQueue(uuid)) {
            queueManager.leaveQueue(e.getPlayer());
        }
        eloManager.onPlayerQuit(uuid);
    }

    @EventHandler
    public void onDriverFinishHeat(DriverFinishHeatEvent e){
        Driver driver = e.getDriver();
        boolean allDriversFinished = driver.getHeat().noDriversRunning();
        RaceManager rm = plugin.getRaceManager();

        if (!queueManager.isRankedRaceActive()) return;
        if (!rm.isRankedHeat(driver.getHeat())) return;

        if (firstFinishers.get(driver.getHeat()) == null){
            firstFinishers.put(driver.getHeat(), driver);
            // if your seeing this please remind me to move this to the config file or some sort of command later i hate hard coding stuff but am leaving this becasue i am lazy
            rm.countdownTimer(driver, "1m");
        }

        if (allDriversFinished){
            rm.cancelCountdown(driver);
            firstFinishers.remove(driver.getHeat());
        }
    }
}
