package org.widnees.widCore.manager.chatguard;

public class ChatGuardResult {
    private final boolean allowed;
    private final Type type;
    private final String data;

    public ChatGuardResult(boolean allowed, Type type, String data) {
        this.allowed = allowed;
        this.type = type;
        this.data = data;
    }

    public boolean isAllowed() {
        return this.allowed;
    }

    public Type getType() {
        return this.type;
    }

    public String getData() {
        return this.data;
    }

    public static ChatGuardResult allowed() {
        return new ChatGuardResult(true, Type.ALLOWED, null);
    }

    public static enum Type {
        ALLOWED,
        BANNED_WORD,
        BANNED_WORD_SYMBOL,
        BANNED_WORD_SQUEEZED,
        BANNED_WORD_CONSONANT,
        SPAM,
        FLOOD_REPEAT,
        FLOOD_SIMILAR,
        ADVERTISEMENT_DOMAIN,
        ADVERTISEMENT_IP,
        ADVERTISEMENT_DISCORD;

    }
}
