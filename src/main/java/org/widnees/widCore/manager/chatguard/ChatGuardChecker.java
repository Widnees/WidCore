package org.widnees.widCore.manager.chatguard;

import org.bukkit.entity.Player;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;

public interface ChatGuardChecker {
    public ChatGuardResult check(Player var1, String var2);

    public void reload();
}
