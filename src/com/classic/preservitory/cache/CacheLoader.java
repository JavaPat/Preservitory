package com.classic.preservitory.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CacheLoader {

    public static byte[] getFile(String name) {
        try {
            String path = CacheConfig.CACHE_DIR + name;
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            System.err.println("Missing asset: " + name);
            return null;
        }
    }
}
