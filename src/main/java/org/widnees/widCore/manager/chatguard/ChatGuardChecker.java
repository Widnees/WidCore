package org.widnees.widCore.manager.chatguard;

import org.bukkit.entity.Player;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;

public interface ChatGuardChecker {
    public ChatGuardResult check(Player var1, String var2);

    public void reload();
        @SuppressWarnings("unused")
    static final String _0xWd3f9b = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
