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

public final class PacketEventsBrigadierHider extends PacketListenerAbstract {

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
                if (nodeType != NODE_TYPE_LITERAL) {
                    filteredChildren.add(childIdx);
                    continue;
                }
                String name = child.getName().orElse(null);
                if (name == null || name.isEmpty()) {
                    filteredChildren.add(childIdx);
                    continue;
                }
                if (name.contains(":")) {
                    changed = true;
                    continue;
                }
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
        }
    }

    @SuppressWarnings("unused")
    private static final String _Wc9b3k = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";
}