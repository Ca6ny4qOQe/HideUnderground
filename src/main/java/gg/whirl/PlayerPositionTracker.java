package gg.whirl;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class PlayerPositionTracker implements Runnable {
    private final HideUnderground plugin;

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isWorldEnabled(player.getWorld().getName())) {
                continue;
            }

            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, task -> checkPlayer(player), null);
            } else {
                checkPlayer(player);
            }
        }
    }

    private void checkPlayer(Player player) {
        if (!plugin.isWorldEnabled(player.getWorld().getName())) {
            return;
        }

        double playerY = player.getLocation().getY();
        int revealY = plugin.getRevealYLevel();

        boolean currentlyBelow = playerY <= revealY;
        boolean wasBelow = plugin.isPlayerBelowThreshold(player.getUniqueId());

        boolean currentlyHasBypass = player.hasPermission("hideunderground.bypass");
        boolean hadBypass = plugin.hasPlayerBypass(player.getUniqueId());

        plugin.setPlayerBelowThreshold(player.getUniqueId(), currentlyBelow);
        plugin.setPlayerBypass(player.getUniqueId(), currentlyHasBypass);

        if (currentlyBelow != wasBelow || currentlyHasBypass != hadBypass) {
            if (!currentlyBelow && wasBelow && !currentlyHasBypass) {
                plugin.getEntityPacketListener().hideEntitiesForPlayer(player);
            }
            refreshPlayerChunks(player);
        }
    }

    private void refreshPlayerChunks(Player player) {
        int chunkRadius = plugin.getChunkReloadRadius();
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;

        if (plugin.isFolia()) {
            for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
                for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                    int chunkX = x;
                    int chunkZ = z;
                    Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, task -> {
                        try {
                            if (world.isChunkLoaded(chunkX, chunkZ)) {
                                world.refreshChunk(chunkX, chunkZ);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error refreshing chunk at " + chunkX + ", " + chunkZ + ": " + e.getMessage());
                        }
                    });
                }
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
                    for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                        if (world.isChunkLoaded(x, z)) {
                            world.refreshChunk(x, z);
                        }
                    }
                }
            });
        }
    }
}