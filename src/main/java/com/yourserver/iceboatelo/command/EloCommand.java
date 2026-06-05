package com.yourserver.iceboatelo.command;

import com.yourserver.iceboatelo.IceBoatElo;
import com.yourserver.iceboatelo.manager.EloManager;
import com.yourserver.iceboatelo.manager.QueueManager;
import com.yourserver.iceboatelo.model.EloData;
import me.makkuusen.timing.system.database.TrackDatabase;
import me.makkuusen.timing.system.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class EloCommand implements CommandExecutor, TabCompleter {

    private final IceBoatElo plugin;
    private final EloManager eloManager;
    private final QueueManager queueManager;

    private static final String PRE = "§8[§6Ranked§8] §r";

    public EloCommand(IceBoatElo plugin, EloManager eloManager, QueueManager queueManager) {
        this.plugin = plugin;
        this.eloManager = eloManager;
        this.queueManager = queueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "info"                       -> handleInfo(sender, args);
            case "top", "leaderboard", "lb"   -> handleTop(sender, args);
            case "queue", "join"              -> handleQueue(sender);
            case "leave", "dequeue"           -> handleLeave(sender);
            case "queueinfo", "qi"            -> handleQueueInfo(sender);
            case "testresult"                 -> handleTestResult(sender, args);
            case "reload"                     -> handleReload(sender);
            case "track"                      -> handleTrack(sender, args); // yeah i'll maintain the line i guess it looks nice
            case "forcestart"                 -> handleForceStart(sender);
            case "enable"                     -> setRankedEnabled(sender, true);
            case "disable"                     -> setRankedEnabled(sender, false);
            case "toggle"                     -> toggleRanked(sender);
            default                           -> sendHelp(sender);
        }
        return true;
    }

    private void setRankedEnabled(CommandSender sender, Boolean state){
        plugin.getQueueManager().setEnabled(state);
        sender.sendMessage(PRE + "Ranked is now " + (state ? "§aenabled" : "§cdisabled"));
    }

    private void toggleRanked(CommandSender sender){
        plugin.getQueueManager().toggleEnabled();
        sender.sendMessage(PRE + "Ranked is now " +
                (plugin.getQueueManager().isEnabled() ? "§aenabled" : "§cdisabled"));
    }

    // ─── /elo info [player] ──────────────────────────────────────────────

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(PRE + "§cPlayer not found or offline."); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(PRE + "§cUsage: /elo info <player>"); return;
        }

        EloData data = eloManager.getPlayer(target.getUniqueId());
        int rank     = eloManager.getPlayerRank(target.getUniqueId());
        int toNext   = data.getEloToNextRank();
        String nextRank = data.getNextRankName();
        String wr    = String.format("%.1f", data.getWinRate());

        sender.sendMessage("");
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("  " + data.getRankIcon() + "  §e§l" + data.getName() + "'s Ranked Stats");
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("  §7Rank       §f" + data.getRankDisplay());
        sender.sendMessage("  §7ELO        §e" + data.getElo());
        sender.sendMessage("  §7Position   §f#" + rank + " globally");
        sender.sendMessage("  §7Races      §f" + data.getRacesPlayed());
        sender.sendMessage("  §7Wins       §a" + data.getWins());
        sender.sendMessage("  §7Losses     §c" + data.getLosses());
        sender.sendMessage("  §7Win Rate   §f" + wr + "%");

        if (toNext == -1) {
            sender.sendMessage("  §6✦ Max rank achieved!");
        } else {
            sender.sendMessage("  §7Next Rank   " + nextRank + " §8(§e" + toNext + " ELO away§8)");
            // Progress bar
            sender.sendMessage("  " + buildProgressBar(data));
        }
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    private String buildProgressBar(EloData data) {
        int toNext = data.getEloToNextRank();
        if (toNext == -1) return "§6██████████ MAX";

        // Work out current rank threshold to compute progress within this tier
        int currentThreshold = data.getElo() - (data.getEloToNextRank() - toNext);

        // Simple: find the gap between current rank threshold and next rank threshold
        // We know toNext = nextThreshold - elo
        // We need currentRankThreshold
        // Use config to find it
        var section = plugin.getConfig().getConfigurationSection("ranks");
        int currentRankThreshold = 0;
        if (section != null) {
            List<Integer> thresholds = section.getKeys(false).stream()
                    .map(k -> section.getInt(k))
                    .sorted()
                    .collect(Collectors.toList());
            for (int t : thresholds) {
                if (t <= data.getElo()) currentRankThreshold = t;
            }
        }

        int tierSize = (data.getElo() + toNext) - currentRankThreshold;
        int progress = data.getElo() - currentRankThreshold;
        double pct = tierSize > 0 ? (double) progress / tierSize : 0;

        int filled = (int) (pct * 20);
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < 20; i++) {
            if (i == filled) bar.append("§7");
            bar.append("█");
        }
        bar.append("  §f").append(String.format("%.0f", pct * 100)).append("%");
        return bar.toString();
    }

    // ─── /elo top ────────────────────────────────────────────────────────

    private void handleTop(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length >= 2) {
            try { limit = Math.min(25, Math.max(1, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {}
        }

        List<EloData> top = eloManager.getLeaderboard(limit);

        sender.sendMessage("");
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l      🏆 ELO LEADERBOARD 🏆");
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String[] medals = {"§6§l#1", "§7§l#2", "§c§l#3"};
        for (int i = 0; i < top.size(); i++) {
            EloData d = top.get(i);
            String pos = i < medals.length ? medals[i] : "§f§l#" + (i + 1);
            sender.sendMessage("  " + pos + "  §f" + d.getName()
                    + "  §8|  " + d.getRankDisplay()
                    + "  §8|  §e" + d.getElo() + " ELO"
                    + "  §8|  §a" + d.getWins() + "W §c" + d.getLosses() + "L");
        }
        sender.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    // ─── /elo queue ──────────────────────────────────────────────────────

    private void handleQueue(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PRE + "§cOnly players can queue."); return;
        }
        if (queueManager.isInRankedHeat(player.getUniqueId())) {
            player.sendMessage(PRE + "§cYou are already in a ranked race!"); return;
        }
        boolean added = queueManager.joinQueue(player);
        if (!added) player.sendMessage(PRE + "§cYou are already in the queue.");
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PRE + "§cOnly players can leave the queue."); return;
        }
        boolean removed = queueManager.leaveQueue(player);
        if (!removed) player.sendMessage(PRE + "§cYou are not in the queue.");
    }

    private void handleQueueInfo(CommandSender sender) {
        int secs = queueManager.getSecondsRemaining();
        if (secs <= 0) {
            sender.sendMessage(PRE + "No race is being queued right now.");
        } else {
            sender.sendMessage(PRE + "§e" + queueManager.getQueueSize()
                    + " §fplayers in queue — race starts in §e"
                    + formatTime(secs) + "§f.");
        }
    }

    // ─── Admin commands ──────────────────────────────────────────────────

    private void handleTestResult(CommandSender sender, String[] args) {
        if (!sender.hasPermission("iceboatelo.admin")) {
            sender.sendMessage(PRE + "§cNo permission."); return;
        }
        if (args.length < 3) {
            sender.sendMessage(PRE + "§cUsage: /elo testresult <p1> <p2> ... (in finish order)"); return;
        }
        List<UUID> order = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Player p = Bukkit.getPlayer(args[i]);
            if (p == null) { sender.sendMessage(PRE + "§cPlayer §e" + args[i] + " §cnot found."); return; }
            order.add(p.getUniqueId());
        }
        eloManager.processRaceResult(order);
        sender.sendMessage(PRE + "§aTest race processed!");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("iceboatelo.admin")) {
            sender.sendMessage(PRE + "§cNo permission."); return;
        }
        plugin.reloadConfig();
        sender.sendMessage(PRE + "§aConfig reloaded.");
    }

    // for testing purposes mostly because i don't wanna wait for every test
    private void handleForceStart(CommandSender sender){
        if (!sender.hasPermission("iceboatelo.admin")) {
            sender.sendMessage(PRE + "§cNo permission.");
            return;
        }

        queueManager.forceStart();

        sender.sendMessage(PRE + "§aForced ranked race start.");
    }

    // track pool handling and stuff (i need more sleep)
    private void handleTrack(CommandSender sender, String[] args) {

        if (!sender.hasPermission("iceboatelo.admin")) {
            sender.sendMessage(PRE + "§cNo permission.");
            return;
        }


        if (args.length < 2){
            sender.sendMessage(PRE + "§cUsage: /elo track <add|remove|list> [track]");
            return;
        }
        String action = args[1];
        List<String> tracks = plugin.getConfig().getStringList("ranked-tracks");

        if (action.equalsIgnoreCase("list")) {

            sender.sendMessage(PRE + "§eRanked Tracks:");

            for (String track : tracks) {
                sender.sendMessage(" §7- §f" + track);
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(PRE + "§cUsage: /elo track <add|remove|list> [track]");
            return;
        }


        String trackName = args[2];

        Optional<Track> track = TrackDatabase.getTrack(trackName);

        if (track.isEmpty()) {
            sender.sendMessage(PRE + "§cTrack not found in TimingSystem.");
            return;
        }

        if (action.equalsIgnoreCase("add")) {

            if (tracks.contains(trackName)) {
                sender.sendMessage(PRE + "§cTrack already exists in pool.");
                return;
            }

            tracks.add(trackName);

            plugin.getConfig().set("ranked-tracks", tracks);
            plugin.saveConfig();

            sender.sendMessage(PRE + "§aAdded track §e" + trackName + "§a to ranked pool.");
        }
        else if (action.equalsIgnoreCase("remove")) {

            if (!tracks.remove(trackName)) {
                sender.sendMessage(PRE + "§cTrack not found in pool.");
                return;
            }

            plugin.getConfig().set("ranked-tracks", tracks);
            plugin.saveConfig();

            sender.sendMessage(PRE + "§aRemoved track §e" + trackName + "§a from ranked pool.");
        }

        else {
            sender.sendMessage(PRE + "§cUsage: /elo track <add|remove|list> [track]");
        }
    }

    // ─── Help & tab complete ─────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§6§lIceBoat Ranked — Commands");
        sender.sendMessage("§e/elo info §8[player]      §7Your rank, ELO, and progress");
        sender.sendMessage("§e/elo top §8[n]            §7Global leaderboard");
        sender.sendMessage("§e/elo queue               §7Join the ranked queue");
        sender.sendMessage("§e/elo leave               §7Leave the queue");
        sender.sendMessage("§e/elo queueinfo           §7Check queue status");
        if (sender.hasPermission("iceboatelo.admin")) {
            sender.sendMessage("§c/elo testresult <p1> <p2>... §7Simulate result (admin)");
            sender.sendMessage("§c/elo reload              §7Reload config (admin)");
        }
        sender.sendMessage("");
    }

    private String formatTime(int secs) {
        if (secs >= 60) { int m = secs / 60, s = secs % 60; return m + ":" + String.format("%02d", s); }
        return secs + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info", "top", "queue", "leave", "queueinfo"));
            if (sender.hasPermission("iceboatelo.admin")) { subs.add("testresult"); subs.add("reload"); subs.add("track"); subs.add("forcestart");}
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("testresult"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
            return List.of("add", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("track")
                && args[1].equalsIgnoreCase("remove")) {

            return plugin.getConfig().getStringList("ranked-tracks")
                    .stream()
                    .filter(t -> t.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
