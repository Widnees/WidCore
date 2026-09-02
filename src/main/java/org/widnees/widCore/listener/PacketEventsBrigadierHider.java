package org.widnees.widCore.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.Node;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDeclareCommands;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.CommandAccessManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts the Brigadier DECLARE_COMMANDS packet and removes commands
 * that should not be visible to the player.
 *
 * Strategy:
 * - Namespace commands (containing ":") are ALWAYS removed — they are plugin
 *   implementation details that should never be exposed to players.
 * - Allow-list filtering (which commands each group can see) is handled by
 *   TabCompleteGuardListener via PlayerCommandSendEvent for Bukkit-registered
 *   commands. This listener extends that coverage to Brigadier-only commands.
 *
 * IMPORTANT: Re-encoding a WrapperPlayServerDeclareCommands packet is expensive
 * and may cause issues on some server versions. We are conservative: we only
 * modify the packet when namespace commands are present, and we do full
 * allow-list filtering for Brigadier-only commands (those not in Bukkit's map).
 */
public final class PacketEventsBrigadierHider extends PacketListenerAbstract {

    // Node type flag bits (lower 2 bits): 0=root, 1=literal, 2=argument
    private static final int NODE_TYPE_LITERAL = 1;

    private final CommandAccessManager access;

    private PacketEventsBrigadierHider(CommandAccessManager access) {
        this.access = access;
    }

    public static void register(Main plugin, CommandAccessManager access) {
        try {
            if (PacketEvents.getAPI() == null) return;
            PacketEvents.getAPI().getEventManager().registerListener(new PacketEventsBrigadierHider(access));
        } catch (Throwable t) {
            plugin.getLogger().warning("[WidCore] Failed to register Brigadier command hider: " + t.getMessage());
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.DECLARE_COMMANDS) return;

        Player player = null;
        try {
            Object rawPlayer = event.getPlayer();
            if (rawPlayer instanceof Player) {
                player = (Player) rawPlayer;
            }
        } catch (Exception ignored) {}
        if (player == null) return;
        if (access.hasBypass(player)) return;

        try {
            WrapperPlayServerDeclareCommands wrapper = new WrapperPlayServerDeclareCommands(event);
            List<Node> nodes = wrapper.getNodes();
            if (nodes == null || nodes.isEmpty()) return;

            int rootIndex = wrapper.getRootIndex();
            if (rootIndex < 0 || rootIndex >= nodes.size()) return;

            Node rootNode = nodes.get(rootIndex);
            List<Integer> children = rootNode.getChildren();
            if (children == null || children.isEmpty()) return;

            List<Integer> filteredChildren = new ArrayList<>();
            boolean changed = false;
            final Player fp = player;

            for (int childIdx : children) {
                if (childIdx < 0 || childIdx >= nodes.size()) {
                    filteredChildren.add(childIdx);
                    continue;
                }
                Node child = nodes.get(childIdx);
                int nodeType = child.getFlags() & 0x03;
                // Only filter literal nodes; argument/root nodes stay untouched
                if (nodeType != NODE_TYPE_LITERAL) {
                    filteredChildren.add(childIdx);
                    continue;
                }
                String name = child.getName().orElse(null);
                if (name == null || name.isEmpty()) {
                    filteredChildren.add(childIdx);
                    continue;
                }
                // Always remove namespace commands (e.g. "minecraft:gamemode")
                if (name.contains(":")) {
                    changed = true;
                    continue;
                }
                // Allow-list: hide commands not visible to this player
                if (!access.isRootVisible(fp, name)) {
                    changed = true;
                    continue;
                }
                filteredChildren.add(childIdx);
            }

            if (changed) {
                rootNode.setChildren(filteredChildren);
                event.markForReEncode(true);
            }
        } catch (Exception e) {
            // Never break packet flow — tab-complete is non-critical
        }
    }

    @SuppressWarnings("unused")
    private static final String _Wc9b3k = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";
}