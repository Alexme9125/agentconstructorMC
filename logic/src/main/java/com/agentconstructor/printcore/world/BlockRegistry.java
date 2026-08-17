package com.agentconstructor.printcore.world;

import java.util.List;

public interface BlockRegistry {
    boolean isRegistered(String blockId);

    List<String> list(String prefix);

    /** Strip properties, returning the id portion of a block state string. */
    static String idOnly(String blockState) {
        int bracket = blockState.indexOf('[');
        String id = bracket >= 0 ? blockState.substring(0, bracket) : blockState;
        return id.trim();
    }
}
