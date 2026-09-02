package org.widnees.widCore.migrate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB/MySQL dump (.sql) dosyasından INSERT INTO satırlarını parse eden sınıf.
 * bit(1): '\0' = false, raw 0x01 byte = true
 */
public final class LitebansDumpParser {

    static final UUID CONSOLE_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private LitebansDumpParser() {}

    public static List<List<String>> parseTable(File file, String tableName)
            throws IOException {
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(file)) {
            bytes = fis.readAllBytes();
        }
        String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        int idx = content.indexOf("INSERT INTO `" + tableName + "` VALUES");
        if (idx < 0) idx = content.indexOf("INSERT INTO " + tableName + " VALUES");
        if (idx < 0) return new ArrayList<>();

        int start = content.indexOf('(', idx);
        if (start < 0) return new ArrayList<>();

        int end = findEnd(content, start);
        List<List<String>> rows = new ArrayList<>();
        parseRows(content.substring(start, end), rows);
        return rows;
    }

    private static int findEnd(String s, int from) {
        int i = from;
        boolean str = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (str) {
                if (c == '\\') { i += 2; continue; }
                if (c == '\'') str = false;
            } else {
                if (c == '\'') str = true;
                else if (c == ';') return i;
            }
            i++;
        }
        return s.length();
    }

    private static void parseRows(String block, List<List<String>> out) {
        int[] pos = {0};
        int len = block.length();
        while (pos[0] < len) {
            while (pos[0] < len && block.charAt(pos[0]) != '(') pos[0]++;
            if (pos[0] >= len) break;
            pos[0]++;
            List<String> row = new ArrayList<>();
            while (pos[0] < len && block.charAt(pos[0]) != ')') {
                while (pos[0] < len && block.charAt(pos[0]) == ' ') pos[0]++;
                if (pos[0] >= len) break;
                char c = block.charAt(pos[0]);
                if (c == '\'') {
                    pos[0]++;
                    StringBuilder sb = new StringBuilder();
                    while (pos[0] < len) {
                        char ch = block.charAt(pos[0]);
                        if (ch == '\'') {
                            if (pos[0] + 1 < len && block.charAt(pos[0] + 1) == '\'') {
                                sb.append('\''); pos[0] += 2;
                            } else { pos[0]++; break; }
                        } else if (ch == '\\' && pos[0] + 1 < len) {
                            char nx = block.charAt(pos[0] + 1);
                            switch (nx) {
                                case '0': sb.append('\0'); break;
                                case 'n': sb.append('\n'); break;
                                case 'r': sb.append('\r'); break;
                                case 't': sb.append('\t'); break;
                                case '\'': sb.append('\''); break;
                                case '"': sb.append('"'); break;
                                case '\\': sb.append('\\'); break;
                                default: sb.append(nx); break;
                            }
                            pos[0] += 2;
                        } else { sb.append(ch); pos[0]++; }
                    }
                    row.add(sb.toString());
                } else if (len - pos[0] >= 4
                        && block.substring(pos[0], pos[0] + 4).equalsIgnoreCase("NULL")) {
                    row.add(null); pos[0] += 4;
                } else {
                    StringBuilder sb = new StringBuilder();
                    while (pos[0] < len && block.charAt(pos[0]) != ','
                            && block.charAt(pos[0]) != ')') {
                        sb.append(block.charAt(pos[0])); pos[0]++;
                    }
                    row.add(sb.toString().trim());
                }
                while (pos[0] < len
                        && (block.charAt(pos[0]) == ',' || block.charAt(pos[0]) == ' '))
                    pos[0]++;
            }
            if (!row.isEmpty()) out.add(row);
            if (pos[0] < len && block.charAt(pos[0]) == ')') pos[0]++;
        }
    }

    public static boolean isBitTrue(String val) {
        if (val == null || val.isEmpty()) return false;
        for (int i = 0; i < val.length(); i++) if (val.charAt(i) != '\0') return true;
        return false;
    }

    public static UUID parseUuid(String val) {
        if (val == null || val.isEmpty() || val.equalsIgnoreCase("CONSOLE"))
            return CONSOLE_UUID;
        try { return UUID.fromString(val); }
        catch (IllegalArgumentException e) { return CONSOLE_UUID; }
    }

    public static long parseLong(String val) {
        if (val == null || val.isEmpty()) return 0L;
        try { return Long.parseLong(val.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }

    public static String col(List<String> row, int idx) {
        return idx < row.size() ? row.get(idx) : null;
    }
}