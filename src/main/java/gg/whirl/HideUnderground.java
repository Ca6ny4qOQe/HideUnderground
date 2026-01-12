package gg.whirl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HideUnderground extends JavaPlugin implements Listener {
    @Getter
    private static HideUnderground instance;
    
    @Getter
    private int hiddenYLevel;
    
    @Getter
    private int revealYLevel;
    
    @Getter
    private int chunkReloadRadius;
    
    @Getter
    private List<String> enabledWorlds;
    
    @Getter
    private String replacementBlock;
    
    private final Map<UUID, Boolean> playerBelowThreshold = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerBypassStatus = new ConcurrentHashMap<>();
    
    @Getter
    private EntityPacketListener entityPacketListener;
    
    @Getter
    private boolean folia;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
            .reEncodeByDefault(false)
            .checkForUpdates(false)
            .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        folia = checkFolia();
        
        saveDefaultConfig();
        loadConfig();
        
        getServer().getPluginManager().registerEvents(this, this);
        
        PacketEvents.getAPI().getEventManager().registerListener((PacketListenerCommon) new ChunkPacketListener(this));
        
        entityPacketListener = new EntityPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener((PacketListenerCommon) entityPacketListener);
        
        PacketEvents.getAPI().getEventManager().registerListener((PacketListenerCommon) new BlockUpdateListener(this));
        
        PacketEvents.getAPI().init();
        
        if (folia) {
            getLogger().info("Detected Folia - using region-based scheduling");
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> new PlayerPositionTracker(this).run(), 1L, 5L);
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> entityPacketListener.cleanupEntityCache(), 1800L, 1800L);
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> new ChunkReloader(this).run(), 1800L, 1800L);
        } else {
            getLogger().info("Detected Spigot/Paper - using standard scheduling");
            Bukkit.getScheduler().runTaskTimer(this, new PlayerPositionTracker(this), 0L, 5L);
            Bukkit.getScheduler().runTaskTimer(this, () -> entityPacketListener.cleanupEntityCache(), 0L, 1800L);
            Bukkit.getScheduler().runTaskTimer(this, new ChunkReloader(this), 0L, 1800L);
        }
        
        getLogger().info("=================================");
        getLogger().info("HideUnderground enabled!");
        getLogger().info("Platform: " + (folia ? "Folia" : "Spigot/Paper"));
        getLogger().info("Hidden Y Level: " + hiddenYLevel);
        getLogger().info("Reveal Y Level: " + revealYLevel);
        getLogger().info("Enabled Worlds: " + enabledWorlds);
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        playerBelowThreshold.clear();
        playerBypassStatus.clear();
        getLogger().info("HideUnderground disabled!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getUniqueId());
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        hiddenYLevel = config.getInt("hidden-y-level", 16);
        revealYLevel = config.getInt("reveal-y-level", 30);
        chunkReloadRadius = config.getInt("chunk-reload-radius", 10);
        replacementBlock = config.getString("replacement-block", "stone");
        enabledWorlds = config.getStringList("enabled-worlds");
    }

    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    public boolean isPlayerBelowThreshold(UUID playerId) {
        return playerBelowThreshold.getOrDefault(playerId, false);
    }

    public void setPlayerBelowThreshold(UUID playerId, boolean below) {
        playerBelowThreshold.put(playerId, below);
    }

    public void removePlayer(UUID playerId) {
        playerBelowThreshold.remove(playerId);
        playerBypassStatus.remove(playerId);
        if (entityPacketListener != null) {
            entityPacketListener.removePlayer(playerId);
        }
    }

    public boolean hasPlayerBypass(UUID playerId) {
        return playerBypassStatus.getOrDefault(playerId, false);
    }

    public void setPlayerBypass(UUID playerId, boolean hasBypass) {
        playerBypassStatus.put(playerId, hasBypass);
    }
}