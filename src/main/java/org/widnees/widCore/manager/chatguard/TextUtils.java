package org.widnees.widCore.manager.chatguard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextUtils {
    private static final Map<String, String> cleanTextCache = new ConcurrentHashMap<String, String>();
    private static final int MAX_CACHE_SIZE = 1000;

    private TextUtils() {
    }

    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cachedResult = cleanTextCache.get(text);
        if (cachedResult != null) {
            return cachedResult;
        }
        String result = text.toLowerCase().replaceAll("[^a-zA-Z0-9\u00e7\u011f\u0131\u00f6\u015f\u00fc\u00c7\u011eI\u0130\u00d6\u015e\u00dc]", "").trim();
        if (cleanTextCache.size() < 1000) {
            cleanTextCache.put(text, result);
        }
        return result;
    }

    public static String cleanTextBasic(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return TextUtils.normalizeTurkishChars(text.toLowerCase()).replaceAll("[^a-z0-9]", "").trim();
    }

    public static String normalizeTurkishChars(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\u00e7", "c").replace("\u011f", "g").replace("\u0131", "i").replace("\u00f6", "o").replace("\u015f", "s").replace("\u00fc", "u");
    }

    public static double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 100.0;
        }
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 100.0;
        }
        int distance = TextUtils.levenshteinDistance(s1, s2);
        return (double)(maxLen - distance) / (double)maxLen * 100.0;
    }

    public static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        int i = 0;
        while (i <= len1) {
            dp[i][0] = i;
            ++i;
        }
        int j = 0;
        while (j <= len2) {
            dp[0][j] = j;
            ++j;
        }
        i = 1;
        while (i <= len1) {
            int j2 = 1;
            while (j2 <= len2) {
                int cost = s1.charAt(i - 1) == s2.charAt(j2 - 1) ? 0 : 1;
                dp[i][j2] = Math.min(Math.min(dp[i - 1][j2] + 1, dp[i][j2 - 1] + 1), dp[i - 1][j2 - 1] + cost);
                ++j2;
            }
            ++i;
        }
        return dp[len1][len2];
    }

    public static boolean isValidLetter(char c) {
        return Character.isLetterOrDigit(c) || c == '\u00e7' || c == '\u011f' || c == '\u0131' || c == '\u00f6' || c == '\u015f' || c == '\u00fc' || c == '\u00c7' || c == '\u011e' || c == 'I' || c == '\u0130' || c == '\u00d6' || c == '\u015e' || c == '\u00dc';
    }

    public static String removeVowels(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("[ae\u0131io\u00f6u\u00fcAEI\u0130O\u00d6U\u00dc]", "");
    }

    public static int countVowels(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        char[] cArray = text.toCharArray();
        int n = cArray.length;
        int n2 = 0;
        while (n2 < n) {
            char c = cArray[n2];
            if (TextUtils.isVowel(c)) {
                ++count;
            }
            ++n2;
        }
        return count;
    }

    public static boolean isVowel(char c) {
        char lower = Character.toLowerCase(c);
        return lower == 'a' || lower == 'e' || lower == 'i' || lower == 'o' || lower == 'u' || lower == '\u0131' || lower == '\u00f6' || lower == '\u00fc';
    }

    public static void clearCache() {
        cleanTextCache.clear();
    }
}
