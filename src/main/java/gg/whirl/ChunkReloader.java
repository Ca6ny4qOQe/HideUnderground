package gg.whirl;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Objects;

@RequiredArgsConstructor
public class ChunkReloader implements Runnable {
    private final HideUnderground plugin;

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isWorldEnabled(player.getWorld().getName())) {
                continue;
            }
            if (player.hasPermission("hideunderground.bypass")) {
                continue;
            }
            if (plugin.isPlayerBelowThreshold(player.getUniqueId())) {
                continue;
            }

            if (plugin.isFolia()) {
                player.getScheduler().run(plugin, task -> reloadPlayerChunks(player), null);
            } else {
                reloadPlayerChunks(player);
            }
        }
    }

    private void reloadPlayerChunks(Player player) {
        int chunkRadius = plugin.getChunkReloadRadius();
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;

        HashSet<ChunkCoord> chunksToReload = new HashSet<>();
        for (int x = centerX - chunkRadius; x <= centerX + chunkRadius; x++) {
            for (int z = centerZ - chunkRadius; z <= centerZ + chunkRadius; z++) {
                chunksToReload.add(new ChunkCoord(x, z));
            }
        }

        if (plugin.isFolia()) {
            for (ChunkCoord coord : chunksToReload) {
                Bukkit.getRegionScheduler().run(plugin, world, coord.x, coord.z, task -> {
                    try {
                        if (world.isChunkLoaded(coord.x, coord.z)) {
                            world.refreshChunk(coord.x, coord.z);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error reloading chunk at " + coord.x + ", " + coord.z + ": " + e.getMessage());
                    }
                });
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int reloadedCount = 0;
                for (ChunkCoord coord : chunksToReload) {
                    try {
                        if (world.isChunkLoaded(coord.x, coord.z)) {
                            world.refreshChunk(coord.x, coord.z);
                            reloadedCount++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error reloading chunk at " + coord.x + ", " + coord.z + ": " + e.getMessage());
                    }
                }
            });
        }
    }

    private static class ChunkCoord {
        final int x;
        final int z;

        ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoord)) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}