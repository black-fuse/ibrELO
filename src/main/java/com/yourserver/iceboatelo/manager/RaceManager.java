package com.yourserver.iceboatelo.manager;

import com.yourserver.iceboatelo.model.EloData;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.database.EventDatabase;
import me.makkuusen.timing.system.event.Event;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.heat.HeatState;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.round.Round;
import me.makkuusen.timing.system.round.RoundType;
import me.makkuusen.timing.system.track.Track;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class RaceManager {
    private final Plugin plugin;
    private final QueueManager queueManager;
    @Getter
    private final Set<Heat> activeRankedHeats = new HashSet<>();

    public RaceManager(Plugin plugin, QueueManager queueManager){
        this.plugin = plugin;
        this.queueManager = queueManager;
    }

    public Boolean isRankedHeat(Heat heat){
        return activeRankedHeats.contains(heat);
    }

    public boolean startRankedRace(Player player, Track track, int laps, int pits, List<UUID> playerList){
        final UUID raceUUID = UUID.randomUUID();
        final String name = "Ranked_Race_" + raceUUID.toString().substring(0, 8);
        Optional<Event> maybeEvent = EventDatabase.eventNew(player.getUniqueId(), name);
        if (maybeEvent.isEmpty()) return false;
        Event event = maybeEvent.get();
        event.setTrack(track);

        if(!EventDatabase.roundNew(event, RoundType.FINAL, 1)) return false;

        Optional<Round> maybeRound = event.eventSchedule.getRound(1);
        if(maybeRound.isEmpty()) return false;
        Round round = maybeRound.get();

        round.createHeat(1);
        var maybeHeat = round.getHeat("R1F1");
        if(maybeHeat.isEmpty()) return false;
        Heat heat = maybeHeat.get();

        laps = Math.max(1, plugin.getConfig().getInt("race-laps", 5));
        pits = Math.max(0, plugin.getConfig().getInt("race-pits", 0));

        if(track.isStage()) {
            heat.setTotalLaps(1);
            heat.setTotalPits(0);
        } else {
            heat.setTotalLaps(laps);
            heat.setTotalPits(pits);
        }

        HeatState state = heat.getHeatState();
        if(state != HeatState.SETUP && !heat.resetHeat()) return false;

        if (!heat.loadHeat()) {
            EventDatabase.removeEventHard(event);
            event = null;
            round = null;
            heat = null;
            return false;
        }

        List<UUID> sortedPlayers = new ArrayList<>(playerList);

        sortedPlayers.sort(
                Comparator.comparingInt(uuid -> {
                    EloData data = queueManager.getEloManager().getPlayer(uuid);
                    return data != null ? data.getElo() : 800;
                })
        );

        int position = 1;

        for (UUID memberUUID : sortedPlayers) {
            Player member = Bukkit.getPlayer(memberUUID);

            if (member != null) {
                var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(memberUUID);

                if (maybeDriver.isPresent()) {
                    member.sendMessage("§cThe race has started but you're already in a heat!");
                    continue;
                }

                EventDatabase.heatDriverNew(memberUUID, heat, position++);
            }
        }

        if (heat.getHeatState() == HeatState.LOADED) {
            heat.reloadHeat();
        }

        final Heat finalHeat = heat;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finalHeat.startCountdown(10);
        }, 20L);

        int maxRaceTime = 3600 * 20;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (getActiveRankedHeats().contains(finalHeat)) {
                endRankedRace(finalHeat);
            }
        }, maxRaceTime);

        activeRankedHeats.add(heat);
        return true;
    }

    public void countdownTimer(Driver driver,String time){
        Heat heat = driver.getHeat();
        Event event = heat.getEvent();
        int timeLimit = ApiUtilities.parseDurationToMillis(time) / 1000;
        plugin.getLogger().info("parse duration: " + String.valueOf(timeLimit));
        event.eventCountdown.startCountdown(timeLimit, "time left");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!heat.isFinished()) {
                heat.finishHeat();
            }
        }, timeLimit * 20L);
    }

    public void cancelCountdown(Driver driver){
        Heat heat = driver.getHeat();
        Event event = heat.getEvent();
        event.eventCountdown.stopCountdown();
    }

    public void endRankedRace(Heat heat){
        activeRankedHeats.remove(heat);

        Round round = heat.getRound();
        Event event = round.getEvent();

        heat.finishHeat();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            round.finish(event);
        }, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, event::finish, 40L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            EventDatabase.removeEventHard(event);
        }, 60L);
    }

    public void cleanupRaces() {
        plugin.getLogger().info("Cleaning up " + activeRankedHeats.size() + " active ranked race(s)...");
        for (Heat heat : new HashSet<>(activeRankedHeats)) {
            try {
                plugin.getLogger().info("Force-ending ranked race: " + heat.getRound().getEvent().getDisplayName());
                Round round = heat.getRound();
                Event event = round.getEvent();

                heat.finishHeat();
                round.finish(event);
                event.finish();
                EventDatabase.removeEventHard(event);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to cleanup a ranked race: " + e.getMessage());
            }
        }
        activeRankedHeats.clear();
        plugin.getLogger().info("Ranked race cleanup complete.");
    }
}
