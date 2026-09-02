package org.widnees.widCore.manager.chatguard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;
import org.widnees.widCore.manager.chatguard.ChatGuardChecker;
import org.widnees.widCore.manager.chatguard.ChatGuardResult;
import org.widnees.widCore.manager.chatguard.TextUtils;

public class BannedWordChecker
implements ChatGuardChecker {
    private final Main plugin;
    private final ConfigManager configManager;
    private Map<String, String> symbolReplacementMap = new HashMap<String, String>();
    private final Map<String, String> symbolReplacedCache = new ConcurrentHashMap<String, String>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static final double VOWEL_RATIO_THRESHOLD = 0.15;

    public BannedWordChecker(Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.loadSymbolReplacements();
    }

    @Override
    public ChatGuardResult check(Player player, String message) {
        return this.checkBannedWords(message);
    }

    public ChatGuardResult checkBannedWords(String message) {
        FileConfiguration config = this.configManager.getModuleConfig("chatguard/bannedword");
        if (config == null) {
            return ChatGuardResult.allowed();
        }
        List<String> blacklist = config.getStringList("blacklist");
        List<String> whitelist = config.getStringList("whitelist");
        String cleanedMessage = TextUtils.cleanTextBasic(message);
        for (String whiteWord : whitelist) {
            if (!cleanedMessage.contains(TextUtils.cleanTextBasic(whiteWord))) continue;
            return ChatGuardResult.allowed();
        }
        String symbolReplacedMessage = this.replaceSymbols(message);
        String cleanedSymbolMessage = TextUtils.cleanTextBasic(symbolReplacedMessage);
        for (String bannedWord : blacklist) {
            String cleanedBannedWord = TextUtils.cleanTextBasic(bannedWord);
            if (!(cleanedBannedWord.length() <= 3 ? this.containsWordMatch(cleanedSymbolMessage, cleanedBannedWord, message) : cleanedSymbolMessage.contains(cleanedBannedWord))) continue;
            return new ChatGuardResult(false, ChatGuardResult.Type.BANNED_WORD_SYMBOL, bannedWord);
        }
        ChatGuardResult consonantResult = this.checkConsonantMatch(message, blacklist);
        if (!consonantResult.isAllowed()) {
            return consonantResult;
        }
        for (String bannedWord : blacklist) {
            String[] originalWords;
            Pattern p;
            String cleaned = TextUtils.cleanTextBasic(bannedWord);
            if (cleaned.length() < 2 || (p = this.buildInterleavedLettersPattern(cleaned, 2)) == null) continue;
            if (p.matcher(cleanedSymbolMessage).find()) {
                return new ChatGuardResult(false, ChatGuardResult.Type.BANNED_WORD_SQUEEZED, bannedWord);
            }
            String[] stringArray = originalWords = symbolReplacedMessage.toLowerCase().split("[^a-zA-Z0-9\u00e7\u011f\u0131\u00f6\u015f\u00fc\u00c7\u011eI\u0130\u00d6\u015e\u00dc]+");
            int n = originalWords.length;
            int n2 = 0;
            while (n2 < n) {
                String word = stringArray[n2];
                String cleanedWord = TextUtils.cleanTextBasic(word);
                if (cleanedWord.length() >= cleaned.length() && cleanedWord.length() <= cleaned.length() + 3 && p.matcher(cleanedWord).find()) {
                    return new ChatGuardResult(false, ChatGuardResult.Type.BANNED_WORD_SQUEEZED, bannedWord);
                }
                ++n2;
            }
        }
        String squeezedMessage = this.removeSqueezedCharacters(cleanedSymbolMessage);
        for (String bannedWord : blacklist) {
            String cleanedBannedWord = TextUtils.cleanTextBasic(bannedWord);
            if (!(cleanedBannedWord.length() <= 3 ? this.containsWordMatch(squeezedMessage, cleanedBannedWord, message) : squeezedMessage.contains(cleanedBannedWord))) continue;
            return new ChatGuardResult(false, ChatGuardResult.Type.BANNED_WORD_SQUEEZED, bannedWord);
        }
        String normalCleanedMessage = TextUtils.cleanText(message);
        for (String bannedWord : blacklist) {
            String cleanedBannedWord = TextUtils.cleanText(bannedWord);
            if (!(cleanedBannedWord.length() <= 3 ? this.containsWordMatch(normalCleanedMessage, cleanedBannedWord, message) : normalCleanedMessage.contains(cleanedBannedWord))) continue;
            return new ChatGuardResult(false, ChatGuardResult.Type.BANNED_WORD, bannedWord);
        }
        return ChatGuardResult.allowed();
    }

    private boolean containsWordMatch(String cleanedFullMessage, String cleanedBannedWord, String originalMessage) {
        String[] words;
        String[] stringArray = words = originalMessage.toLowerCase().split("[^a-zA-Z0-9\u00e7\u011f\u0131\u00f6\u015f\u00fc\u00c7\u011eI\u0130\u00d6\u015e\u00dc]+");
        int n = words.length;
        int n2 = 0;
        while (n2 < n) {
            String word = stringArray[n2];
            String cleanedWord = TextUtils.cleanTextBasic(word);
            if (cleanedWord.equals(cleanedBannedWord)) {
                return true;
            }
            ++n2;
        }
        return false;
    }

    private ChatGuardResult checkConsonantMatch(String message, List<String> blacklist) {
        String[] words;
        String[] stringArray = words = message.toLowerCase().split("[^a-zA-Z0-9\u00e7\u011f\u0131\u00f6\u015f\u00fc\u00c7\u011eI\u0130\u00d6\u015e\u00dc]+");
        int n = words.length;
        int n2 = 0;
        while (n2 < n) {
            String wordConsonants;
            int vowelCount;
            double vowelRatio;
            String cleanedWord;
            String word = stringArray[n2];
            if (!(word.isEmpty() || (cleanedWord = TextUtils.cleanTextBasic(word)).length() < 2 || (vowelRatio = (double)(vowelCount = TextUtils.countVowels(cleanedWord)) / (double)cleanedWord.length()) > 0.15 || (wordConsonants = TextUtils.removeVowels(cleanedWord)).isEmpty())) {
                for (String bannedWord : blacklist) {
                    String cleanedBanned = TextUtils.cleanTextBasic(bannedWord);
                    String bannedConsonants = TextUtils.removeVowels(cleanedBanned);
                    if (bannedConsonants.length() < 2 || !wordConsonants.equals(bannedConsonants)) continue;
                    return new ChatGuardResult(false, ChatGuardResult.Type.BANNED_WORD_CONSONANT, bannedWord);
                }
            }
            ++n2;
        }
        return ChatGuardResult.allowed();
    }

    @Override
    public void reload() {
        this.loadSymbolReplacements();
        this.symbolReplacedCache.clear();
    }

    private void loadSymbolReplacements() {
        FileConfiguration config = this.configManager.getModuleConfig("chatguard/bannedword");
        if (config != null && config.isConfigurationSection("symbol-replacement")) {
            this.symbolReplacementMap = new HashMap<String, String>();
            for (String symbol : config.getConfigurationSection("symbol-replacement").getKeys(false)) {
                String replacement = config.getString("symbol-replacement." + symbol);
                this.symbolReplacementMap.put(symbol, replacement);
            }
        }
    }

    private String replaceSymbols(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cachedResult = this.symbolReplacedCache.get(text);
        if (cachedResult != null) {
            return cachedResult;
        }
        String result = text.toLowerCase();
        for (Map.Entry<String, String> entry : this.symbolReplacementMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        if (this.symbolReplacedCache.size() < 1000) {
            this.symbolReplacedCache.put(text, result);
        }
        return result;
    }

    private String removeSqueezedCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        char[] chars = text.toCharArray();
        int i = 0;
        while (i < chars.length) {
            char currentChar = chars[i];
            if (TextUtils.isValidLetter(currentChar)) {
                result.append(currentChar);
                int j = i + 1;
                while (j < chars.length && !TextUtils.isValidLetter(chars[j])) {
                    ++j;
                }
                if (j < chars.length && j > i + 1) {
                    i = j - 1;
                }
            }
            ++i;
        }
        return result.toString();
    }

    private Pattern buildInterleavedLettersPattern(String word, int maxGap) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < word.length()) {
            char c = word.charAt(i);
            if (i == 0) {
                sb.append(Pattern.quote(String.valueOf(c)));
            } else {
                sb.append(".{0,").append(Math.max(0, maxGap)).append("}");
                sb.append(Pattern.quote(String.valueOf(c)));
            }
            ++i;
        }
        try {
            return Pattern.compile(sb.toString());
        }
        catch (Exception e) {
            return null;
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xNe3s7b = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
