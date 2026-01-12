package gg.whirl;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.entity.Player;

public class ChunkPacketListener extends PacketListenerAbstract {
    private final HideUnderground plugin;
    private WrappedBlockState replacementState;

    public ChunkPacketListener(HideUnderground plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        initializeReplacementBlock();
    }

    private void initializeReplacementBlock() {
        try {
            String blockType = plugin.getReplacementBlock().toLowerCase();
            if (!blockType.contains(":")) {
                blockType = "minecraft:" + blockType;
            }
            replacementState = WrappedBlockState.getByString(blockType);
            plugin.getLogger().info("Using replacement block: " + blockType);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid replacement block, defaulting to stone: " + e.getMessage());
            replacementState = WrappedBlockState.getByString("minecraft:stone");
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (player == null) {
            return;
        }

        String worldName = player.getWorld().getName();
        if (!plugin.isWorldEnabled(worldName)) {
            return;
        }

        if (player.hasPermission("hideunderground.bypass")) {
            return;
        }

        try {
            WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
            Column column = packet.getColumn();
            if (column == null) {
                return;
            }

            int hiddenY = plugin.getHiddenYLevel();
            int minY = player.getWorld().getMinHeight();
            BaseChunk[] chunks = column.getChunks();
            if (chunks == null) {
                return;
            }

            boolean playerBelowThreshold = plugin.isPlayerBelowThreshold(player.getUniqueId());
            boolean modified = false;

            for (int i = 0; i < chunks.length; i++) {
                BaseChunk chunk = chunks[i];
                if (chunk == null) continue;

                int chunkSectionY = minY + i * 16;
                int chunkSectionMaxY = chunkSectionY + 15;

                if (playerBelowThreshold) {
                    continue;
                }

                if (chunkSectionMaxY < hiddenY) {
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                chunk.set(x, y, z, replacementState);
                            }
                        }
                    }
                    modified = true;
                } else if (chunkSectionY < hiddenY) {
                    int maxLocalY = hiddenY - chunkSectionY;
                    int upper = Math.min(maxLocalY, 16);

                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < upper; y++) {
                            for (int z = 0; z < 16; z++) {
                                chunk.set(x, y, z, replacementState);
                            }
                        }
                    }
                    modified = true;
                }
            }

            if (modified) {
                packet.setColumn(column);
                event.markForReEncode(true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error modifying chunk packet: " + e.getMessage());
        }
    }
}