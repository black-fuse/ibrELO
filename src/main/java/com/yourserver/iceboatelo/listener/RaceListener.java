package com.yourserver.iceboatelo.listener;

import com.yourserver.iceboatelo.IceBoatElo;
import com.yourserver.iceboatelo.manager.EloManager;
import com.yourserver.iceboatelo.manager.QueueManager;
import me.makkuusen.timing.system.api.events.HeatFinishEvent;
import me.makkuusen.timing.system.heat.Heat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RaceListener implements Listener {

    private final IceBoatElo plugin;
    private final EloManager eloManager;
    private final QueueManager queueManager;

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
}
