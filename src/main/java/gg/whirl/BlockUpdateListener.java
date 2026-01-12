package gg.whirl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.entity.Player;

public class BlockUpdateListener extends PacketListenerAbstract {
    private final HideUnderground plugin;
    private WrappedBlockState replacementState;
    private WrappedBlockState airState;

    public BlockUpdateListener(HideUnderground plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        initializeReplacementBlock();
    }

    private void initializeReplacementBlock() {
        try {
            replacementState = WrappedBlockState.getByString("minecraft:stone");
            airState = WrappedBlockState.getByString("minecraft:air");
            plugin.getLogger().info("Using replacement block: stone");
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing replacement block: " + e.getMessage());
            replacementState = WrappedBlockState.getByString("minecraft:stone");
            airState = WrappedBlockState.getByString("minecraft:air");
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        String worldName = player.getWorld().getName();
        if (!plugin.isWorldEnabled(worldName)) {
            return;
        }

        if (player.hasPermission("hideunderground.bypass")) {
            return;
        }

        boolean playerBelowThreshold = plugin.isPlayerBelowThreshold(player.getUniqueId());
        if (playerBelowThreshold) {
            return;
        }

        int hiddenY = plugin.getHiddenYLevel();

        try {
            if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
                Vector3i chunkPos = packet.getChunkPosition();
                if (chunkPos == null) {
                    return;
                }

                int sectionY = chunkPos.getY();
                int minY = player.getWorld().getMinHeight();
                int chunkSectionYWorld = minY + sectionY * 16;

                WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = packet.getBlocks();
                if (chunkSectionYWorld < hiddenY && blocks != null && blocks.length > 0) {
                    boolean modified = false;
                    for (int i = 0; i < blocks.length; i++) {
                        WrapperPlayServerMultiBlockChange.EncodedBlock block = blocks[i];
                        int localY = block.getY();
                        int worldY = chunkSectionYWorld + localY;

                        if (worldY < hiddenY) {
                            WrappedBlockState currentState = block.getBlockState(event.getUser().getClientVersion());
                            if (currentState != null && !isAirBlock(currentState)) {
                                block.setBlockState(replacementState);
                                modified = true;
                            }
                        }
                    }

                    if (modified) {
                        packet.setBlocks(blocks);
                        event.markForReEncode(true);
                    }
                }
            } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_ACTION) {
                WrapperPlayServerBlockAction packet = new WrapperPlayServerBlockAction(event);
                Vector3i blockPos = packet.getBlockPosition();
                if (blockPos != null && blockPos.getY() < hiddenY) {
                    event.setCancelled(true);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error filtering block update packet: " + e.getMessage());
        }
    }

    private boolean isAirBlock(WrappedBlockState state) {
        if (state == null) {
            return true;
        }
        String blockName = state.getType().getName().toString().toLowerCase();
        return blockName.contains("air") || blockName.equals("minecraft:air") || 
               blockName.equals("minecraft:cave_air") || blockName.equals("minecraft:void_air");
    }
}