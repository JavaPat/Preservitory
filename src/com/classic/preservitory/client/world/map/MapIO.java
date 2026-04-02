package com.classic.preservitory.client.world.map;

import com.classic.preservitory.client.editor.EditorObject;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles JSON serialisation and deserialisation of TileMap and EditorObject data.
 * No rendering, no UI, no game logic.
 */
public class MapIO {

    public static void save(TileMap tileMap, List<EditorObject> objects, String filePath) throws IOException {
        int w = tileMap.getWidth();
        int h = tileMap.getHeight();
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("{");
            pw.println("  \"width\": " + w + ",");
            pw.println("  \"height\": " + h + ",");
            pw.println("  \"tiles\": [");
            for (int x = 0; x < w; x++) {
                pw.print("    [");
                for (int y = 0; y < h; y++) {
                    pw.print(tileMap.getTile(x, y));
                    if (y < h - 1) pw.print(", ");
                }
                pw.print("]");
                if (x < w - 1) pw.println(",");
                else           pw.println();
            }
            pw.println("  ],");
            pw.println("  \"objects\": [");
            for (int i = 0; i < objects.size(); i++) {
                EditorObject obj = objects.get(i);
                pw.print("    {\"key\":\"" + obj.key + "\","
                        + "\"tileX\":" + obj.tileX + ","
                        + "\"tileY\":" + obj.tileY + ","
                        + "\"rotation\":" + obj.rotation + "}");
                if (i < objects.size() - 1) pw.println(",");
                else                        pw.println();
            }
            pw.println("  ]");
            pw.println("}");
        }
    }

    public static TileMap load(String filePath) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(filePath)));
        int w = Integer.parseInt(json.replaceAll("[\\s\\S]*\"width\"\\s*:\\s*(\\d+)[\\s\\S]*",  "$1"));
        int h = Integer.parseInt(json.replaceAll("[\\s\\S]*\"height\"\\s*:\\s*(\\d+)[\\s\\S]*", "$1"));
        TileMap tileMap = new TileMap(w, h);
        // Extract tiles array (stops at the first ']' after '[', skipping nested)
        String tilesBlock = json.replaceAll("[\\s\\S]*\"tiles\"\\s*:\\s*\\[([\\s\\S]*?)\\]\\s*,?\\s*\"", "$1").trim();
        Matcher rowMatcher = Pattern.compile("\\[([^\\]]+)\\]").matcher(tilesBlock);
        int x = 0;
        while (rowMatcher.find() && x < w) {
            String[] vals = rowMatcher.group(1).trim().split("\\s*,\\s*");
            for (int y = 0; y < vals.length && y < h; y++) {
                tileMap.setTile(x, y, Integer.parseInt(vals[y].trim()));
            }
            x++;
        }
        return tileMap;
    }

    public static List<EditorObject> loadObjects(String filePath) throws IOException {
        List<EditorObject> result = new ArrayList<>();
        String json = new String(Files.readAllBytes(Paths.get(filePath)));
        if (!json.contains("\"objects\"")) return result;
        Matcher arrayMatcher = Pattern.compile(
                "\"objects\"\\s*:\\s*\\[([\\s\\S]*?)\\]\\s*\\}\\s*$").matcher(json);
        if (!arrayMatcher.find()) return result;
        String block = arrayMatcher.group(1).trim();
        if (block.isEmpty()) return result;
        Matcher objMatcher = Pattern.compile("\\{([^}]+)\\}").matcher(block);
        while (objMatcher.find()) {
            String entry    = objMatcher.group(1);
            String key      = extractString(entry, "key");
            int    tileX    = extractInt(entry, "tileX");
            int    tileY    = extractInt(entry, "tileY");
            int    rotation = extractInt(entry, "rotation");
            if (key != null) result.add(new EditorObject(key, tileX, tileY, rotation));
        }
        return result;
    }

    private static String extractString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static int extractInt(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
