package org.widnees.widCore.manager.chatguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.chatguard.ChatGuardChecker;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;
import org.widnees.widCore.manager.chatguard.TextUtils;

public class AdvertisementChecker
implements ChatGuardChecker {
    private final Main plugin;
    private final ConfigManager configManager;
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

    private static final Pattern IP_WITH_WORDS_PATTERN = Pattern.compile("\\b\\d{1,3}\\s*(nokta|\\.)\\s*\\d{1,3}\\s*(nokta|\\.)\\s*\\d{1,3}\\s*(nokta|\\.)\\s*\\d{1,3}\\b", 2);
    private static final Pattern IP_SPACED_PATTERN = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\s*\\.?\\s*){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");
    private static final Set<String> COORDINATE_KEYWORDS = new HashSet<String>(Arrays.asList("x", "y", "z", "xyz", "koordinat", "koord", "kord", "tp", "pos", "loc", "ev", "yer", "gel", "git", "blok", "block", "chunk", "spawn", "warp", "home", "sethome", "tpa", "location", "coord", "coords"));
    private static final Pattern COORDINATE_PATTERN = Pattern.compile("(?i)(?:[xyz]\\s*[:=]\\s*-?\\d+|\\b(?:koordinat|koord|kord|coord|coords|tp|pos|loc|sethome|home|warp)\\b)", 2);
    private final ConcurrentHashMap<String, Boolean> ipPingCache = new ConcurrentHashMap<String, Boolean>();

    public AdvertisementChecker(Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.configManager.getModuleConfig("chatguard/advertisement");
    }

    @Override
    public ChatGuardResult check(Player player, String message) {
        return this.checkAdvertisement(message);
    }

    public ChatGuardResult checkAdvertisement(String message) {
        Matcher matcher;
        FileConfiguration config = this.configManager.getModuleConfig("chatguard/advertisement");
        if (config == null) {
            return ChatGuardResult.allowed();
        }
        String cleanedMessage = TextUtils.cleanText(message);
        String originalMessage = message.toLowerCase();

        if (config.getBoolean("discord.block-invites", true)) {
            String noSpaceMessage = message.replaceAll("\\s+", "").toLowerCase();

            matcher = Pattern.compile("discord\\.?gg/?([a-zA-Z0-9]+)").matcher(noSpaceMessage);
            while (matcher.find()) {
                String serverId = matcher.group(1);
                List<?> whitelistServers = config.getStringList("discord.whitelist-servers");
                if (!whitelistServers.contains(serverId)) {
                    return new ChatGuardResult(false, ChatGuardResult.Type.ADVERTISEMENT_DISCORD, null);
                }
            }

            if (config.getBoolean("discord.block-shorts", false)) {
                Matcher shortMatcher = Pattern.compile("(?:^|\\s)/(?:\\s*)([a-zA-Z0-9]{2,32})\\b").matcher(originalMessage);
                while (shortMatcher.find()) {
                    String serverId = shortMatcher.group(1);
                    List<?> whitelistServers = config.getStringList("discord.whitelist-servers");
                    if (!whitelistServers.contains(serverId)) {
                        return new ChatGuardResult(false, ChatGuardResult.Type.ADVERTISEMENT_DISCORD, null);
                    }
                }
            }
        }

        List<String> whitelistDomains = config.getStringList("whitelist-domains");
        for (String domain : whitelistDomains) {
            if (!originalMessage.contains(domain.toLowerCase()) && !cleanedMessage.contains(domain.toLowerCase().replaceAll("[^a-z]", ""))) continue;
            return ChatGuardResult.allowed();
        }
        List<String> blacklistExtensions = config.getStringList("blacklist-extensions");
        for (String extension : blacklistExtensions) {
            if (this.containsDomainWithExtension(originalMessage, extension)) {
                return new ChatGuardResult(false, ChatGuardResult.Type.ADVERTISEMENT_DOMAIN, extension);
            }
            if (!this.containsDomainWithExtension(cleanedMessage, extension)) continue;
            return new ChatGuardResult(false, ChatGuardResult.Type.ADVERTISEMENT_DOMAIN, extension);
        }
        if (config.getBoolean("block-ip-addresses", true)) {
            String foundIp = this.findIPAddress(originalMessage);
            if (foundIp == null) {
                foundIp = this.findIPAddress(cleanedMessage);
            }
            if (foundIp != null) {
                boolean verifyIp = config.getBoolean("verify-ip-with-ping", true);
                if (!verifyIp || this.isIPActiveCached(foundIp)) {
                    return new ChatGuardResult(false, ChatGuardResult.Type.ADVERTISEMENT_IP, foundIp);
                }
            }
        }
        return ChatGuardResult.allowed();
    }

    @Override
    public void reload() {
    }

    private boolean containsDomainWithExtension(String text, String extension) {
        String pattern = "\\b[a-zA-Z0-9][a-zA-Z0-9-]*\\." + Pattern.quote(extension) + "\\b";
        return Pattern.compile(pattern, 2).matcher(text).find();
    }

    private boolean isIPActiveCached(String potentialIp) {
        if (potentialIp == null || potentialIp.isEmpty()) {
            return false;
        }
        String cleanIp = potentialIp.replaceAll("[^0-9.]", "").replaceAll("\\.+", ".");
        String[] parts = cleanIp.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        if (this.ipPingCache.containsKey(cleanIp)) {
            return this.ipPingCache.get(cleanIp);
        }

        boolean active = false;
        try {
            InetAddress address = InetAddress.getByName(cleanIp);
            active = address.isReachable(500); 
            if (!active) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(address, 25565), 500);
                    active = true;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            active = false;
        }

        if (this.ipPingCache.size() < 2000) {
            this.ipPingCache.put(cleanIp, active);
        }
        return active;
    }

    private String findIPAddress(String text) {
        String smart;
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher numMatcherQuick = Pattern.compile("\\d{1,3}").matcher(text);
        int numCount = 0;
        while (numMatcherQuick.find()) {
            if (++numCount >= 4) break;
        }
        if (numCount < 4) {
            return null;
        }
        if (this.containsVersionLikeRange(text)) {
            return null;
        }
        Matcher matcher = IPV4_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        matcher = IP_WITH_WORDS_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        if (this.hasCoordinateContext(text)) {
            return null;
        }
        matcher = IP_SPACED_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        String normalizedText = this.normalizeIPText(text);
        matcher = IPV4_PATTERN.matcher(normalizedText);
        if (matcher.find()) {
            return matcher.group();
        }
        if (!this.containsLetters(text)) {
            String digitsSeparated = text.replaceAll("[^0-9]+", ".");
            if (!(digitsSeparated = digitsSeparated.replaceAll("\\.+", ".").replaceAll("^\\.+|\\.+$", "")).isEmpty() && (matcher = IPV4_PATTERN.matcher(digitsSeparated)).find()) {
                return matcher.group();
            }
        }
        if ((smart = this.detectIpWithSmartSeparator(text, 10)) != null) {
            return smart;
        }
        return this.findMixedIPPattern(text);
    }

    private String normalizeIPText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.toLowerCase().replaceAll("\\b(nokta|point|dot)\\b", ".").replaceAll("(\\d+)[^\\d.\\s]+(\\d+)", "$1.$2").replaceAll("\\s+", " ").replaceAll("(\\d+)\\s+(\\d+)", "$1.$2").replaceAll("\\.+", ".");
        return normalized.trim();
    }

    private String findMixedIPPattern(String text) {
        Pattern mixedPattern = Pattern.compile("(?:\\b|^)(?:\\d+[^\\d\\s]*){3}\\d+(?:\\b|$)");
        Matcher matcher = mixedPattern.matcher(text);
        while (matcher.find()) {
            String numbersOnly;
            String[] numbers;
            long dotCount;
            String candidate = matcher.group();
            if (candidate.contains("-") || candidate.contains("\u2013") || candidate.contains("\u2014") || (dotCount = candidate.chars().filter(ch -> ch == 46).count()) < 3L || (numbers = (numbersOnly = candidate.replaceAll("[^\\d]", " ")).trim().split("\\s+")).length != 4) continue;
            boolean isValidIP = true;
            String[] stringArray = numbers;
            int n = numbers.length;
            int n2 = 0;
            while (n2 < n) {
                block5: {
                    String num = stringArray[n2];
                    try {
                        int value = Integer.parseInt(num);
                        if (value < 0 || value > 255) {
                            isValidIP = false;
                        }
                        break block5;
                    }
                    catch (NumberFormatException e) {
                        isValidIP = false;
                    }
                    break;
                }
                ++n2;
            }
            if (!isValidIP) continue;
            return candidate;
        }
        return null;
    }

    private boolean containsVersionLikeRange(String text) {
        if (text == null) {
            return false;
        }
        Pattern versionRange = Pattern.compile("\\b\\d+(?:\\.\\d+){1,2}\\s*[-\u2013\u2014]\\s*\\d+(?:\\.\\d+){1,2}\\b");
        return versionRange.matcher(text).find();
    }

    private boolean containsLetters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return Pattern.compile("[a-zA-Z\u00e7\u011f\u0131\u00f6\u015f\u00fc\u00c7\u011eI\u0130\u00d6\u015e\u00dc]").matcher(text).find();
    }

    private String detectIpWithSmartSeparator(String text, int maxLetterRun) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        ArrayList<String> octets = new ArrayList<String>();
        Matcher token = Pattern.compile("(\\d{1,3})|([^\\d]+)").matcher(text);
        String lastType = null;
        while (token.find()) {
            String d = token.group(1);
            String s = token.group(2);
            if (d != null) {
                octets.add(d);
                lastType = "d";
                if (octets.size() <= 4) continue;
                return null;
            }
            if (s == null || !"d".equals(lastType)) continue;
            int letters = 0;
            int i = 0;
            while (i < s.length()) {
                if (Character.isLetter(s.charAt(i))) {
                    ++letters;
                }
                ++i;
            }
            if (letters >= maxLetterRun) {
                return null;
            }
            lastType = "s";
        }
        if (octets.size() != 4) {
            return null;
        }
        for (String o : octets) {
            try {
                int v = Integer.parseInt(o);
                if (v >= 0 && v <= 255) continue;
                return null;
            }
            catch (NumberFormatException e) {
                return null;
            }
        }
        String candidate = String.join((CharSequence)".", octets);
        return IPV4_PATTERN.matcher(candidate).matches() ? candidate : null;
    }

    private boolean hasCoordinateContext(String text) {
        String[] words;
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        if (COORDINATE_PATTERN.matcher(lower).find()) {
            return true;
        }
        String[] stringArray = words = lower.split("[\\s,;:=]+");
        int n = words.length;
        int n2 = 0;
        while (n2 < n) {
            String word = stringArray[n2];
            String cleaned = word.replaceAll("[^a-z\u00e7\u011f\u0131\u00f6\u015f\u00fc]", "");
            if (!cleaned.isEmpty() && COORDINATE_KEYWORDS.contains(cleaned)) {
                return true;
            }
            ++n2;
        }
        return false;
    }
}
