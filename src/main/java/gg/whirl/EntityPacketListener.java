package gg.whirl;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityPacketListener extends PacketListenerAbstract {
    private final HideUnderground plugin;
    private final Map<Integer, Double> entityYPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> playerVisibleEntities = new ConcurrentHashMap<>();

    public EntityPacketListener(HideUnderground plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Server)) {
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

        boolean playerBelowThreshold = plugin.isPlayerBelowThreshold(player.getUniqueId());
        int hiddenY = plugin.getHiddenYLevel();

        try {
            PacketType.Play.Server packetType = (PacketType.Play.Server) event.getPacketType();

            if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
                handleSpawnEntity(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
                handleSpawnLivingEntity(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.SPAWN_PLAYER) {
                handleSpawnPlayer(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
                handleEntityTeleport(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                handleEntityRelativeMove(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                handleEntityRelativeMoveAndRotation(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_METADATA) {
                handleEntityMetadata(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_ANIMATION) {
                handleEntityAnimation(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_STATUS) {
                handleEntityStatus(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                handleEntityEquipment(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
                handleEntityHeadLook(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.ENTITY_ROTATION) {
                handleEntityRotation(event, player, playerBelowThreshold, hiddenY);
            } else if (packetType == PacketType.Play.Server.DESTROY_ENTITIES) {
                handleDestroyEntities(event, player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error filtering entity packet for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleSpawnEntity(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
        Vector3d location = packet.getPosition();
        if (location != null) {
            double entityY = location.getY();
            int entityId = packet.getEntityId();
            entityYPositions.put(entityId, entityY);

            if (!playerBelowThreshold && entityY < hiddenY) {
                event.setCancelled(true);
                return;
            }

            if (playerBelowThreshold || entityY >= hiddenY) {
                trackVisibleEntity(player.getUniqueId(), entityId);
            }
        }
    }

    private void handleSpawnLivingEntity(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerSpawnLivingEntity packet = new WrapperPlayServerSpawnLivingEntity(event);
        Vector3d location = packet.getPosition();
        if (location != null) {
            double entityY = location.getY();
            int entityId = packet.getEntityId();
            entityYPositions.put(entityId, entityY);

            if (!playerBelowThreshold && entityY < hiddenY) {
                event.setCancelled(true);
                return;
            }

            if (playerBelowThreshold || entityY >= hiddenY) {
                trackVisibleEntity(player.getUniqueId(), entityId);
            }
        }
    }

    private void handleSpawnPlayer(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
        Vector3d location = packet.getPosition();
        if (location != null) {
            double entityY = location.getY();
            int entityId = packet.getEntityId();
            entityYPositions.put(entityId, entityY);

            if (!playerBelowThreshold && entityY < hiddenY) {
                event.setCancelled(true);
                return;
            }

            if (playerBelowThreshold || entityY >= hiddenY) {
                trackVisibleEntity(player.getUniqueId(), entityId);
            }
        }
    }

    private void handleEntityTeleport(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
        Vector3d location = packet.getPosition();
        if (location != null) {
            double entityY = location.getY();
            int entityId = packet.getEntityId();
            entityYPositions.put(entityId, entityY);

            if (!playerBelowThreshold && entityY < hiddenY) {
                event.setCancelled(true);
                untrackVisibleEntity(player.getUniqueId(), entityId);
                return;
            }

            trackVisibleEntity(player.getUniqueId(), entityId);
        }
    }

    private void handleEntityRelativeMove(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (cachedY != null) {
            double newY = cachedY + packet.getDeltaY();
            entityYPositions.put(entityId, newY);

            if (!playerBelowThreshold && newY < hiddenY) {
                event.setCancelled(true);
                untrackVisibleEntity(player.getUniqueId(), entityId);
            }
        }
    }

    private void handleEntityRelativeMoveAndRotation(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (cachedY != null) {
            double newY = cachedY + packet.getDeltaY();
            entityYPositions.put(entityId, newY);

            if (!playerBelowThreshold && newY < hiddenY) {
                event.setCancelled(true);
                untrackVisibleEntity(player.getUniqueId(), entityId);
            }
        }
    }

    private void handleEntityMetadata(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (!playerBelowThreshold && cachedY != null && cachedY < hiddenY) {
            event.setCancelled(true);
        }
    }

    private void handleEntityAnimation(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (!playerBelowThreshold && cachedY != null && cachedY < hiddenY) {
            event.setCancelled(true);
        }
    }

    private void handleEntityStatus(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityStatus packet = new WrapperPlayServerEntityStatus(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (!playerBelowThreshold && cachedY != null && cachedY < hiddenY) {
            event.setCancelled(true);
        }
    }

    private void handleEntityEquipment(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (!playerBelowThreshold && cachedY != null && cachedY < hiddenY) {
            event.setCancelled(true);
        }
    }

    private void handleEntityHeadLook(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityHeadLook packet = new WrapperPlayServerEntityHeadLook(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (!playerBelowThreshold && cachedY != null && cachedY < hiddenY) {
            event.setCancelled(true);
        }
    }

    private void handleEntityRotation(PacketSendEvent event, Player player, boolean playerBelowThreshold, int hiddenY) {
        WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
        int entityId = packet.getEntityId();
        Double cachedY = entityYPositions.get(entityId);

        if (!playerBelowThreshold && cachedY != null && cachedY < hiddenY) {
            event.setCancelled(true);
        }
    }

    private void handleDestroyEntities(PacketSendEvent event, Player player) {
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
        for (int entityId : packet.getEntityIds()) {
            entityYPositions.remove(entityId);
            untrackVisibleEntity(player.getUniqueId(), entityId);
        }
    }

    private void trackVisibleEntity(UUID playerId, int entityId) {
        playerVisibleEntities.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    private void untrackVisibleEntity(UUID playerId, int entityId) {
        Set<Integer> visible = playerVisibleEntities.get(playerId);
        if (visible != null) {
            visible.remove(entityId);
        }
    }

    public void hideEntitiesForPlayer(Player player) {
        Set<Integer> visibleEntities = playerVisibleEntities.get(player.getUniqueId());
        if (visibleEntities == null || visibleEntities.isEmpty()) {
            return;
        }

        int hiddenY = plugin.getHiddenYLevel();
        int[] entitiesToDestroy = visibleEntities.stream()
            .filter(entityId -> {
                Double y = entityYPositions.get(entityId);
                return y != null && y < hiddenY;
            })
            .mapToInt(Integer::intValue)
            .toArray();

        if (entitiesToDestroy.length > 0) {
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entitiesToDestroy);
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) {
                user.sendPacket(destroyPacket);
            }

            for (int entityId : entitiesToDestroy) {
                visibleEntities.remove(entityId);
            }

            plugin.getLogger().info("Hidden " + entitiesToDestroy.length + " entities below Y=" + hiddenY + " for player " + player.getName());
        }
    }

    public void removePlayer(UUID playerId) {
        playerVisibleEntities.remove(playerId);
    }

    public void cleanupEntityCache() {
        if (entityYPositions.size() > 10000) {
            plugin.getLogger().info("Cleaning entity cache (size: " + entityYPositions.size() + ")");
            entityYPositions.clear();
            playerVisibleEntities.clear();
        }
    }
}