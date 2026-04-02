package com.classic.preservitory.cache;

import com.classic.preservitory.util.Constants;

public class CacheConfig {

    /** Bump this whenever you upload a new cache zip to force clients to re-download. */
    public static final String CACHE_VERSION = "0.1";

    public static final String ROOT_DIR =
            System.getProperty("user.home") + "/." + Constants.GAME_NAME_TO_LOWER + "/";

    public static final String CACHE_DIR =
            ROOT_DIR + "cache/";

    /** Written after a successful extract — compared on next launch to detect stale cache. */
    public static final String VERSION_FILE =
            ROOT_DIR + "version.txt";

    public static final String CACHE_URL =
            "https://www.dropbox.com/scl/fi/1boolpiliw69rud4gwsor/cache.zip?rlkey=3b5yyifpu61tv6ll1kcjp7mcp&st=dlcztsnh&dl=1";

    public static final String CACHE_ZIP =
            CACHE_DIR + "cache.zip";
}
