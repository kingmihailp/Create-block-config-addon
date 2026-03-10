package com.example.createblocklimit.config;

/**
 * Represents a single block-limit rule parsed from the server config.
 *
 * @param blockId  Registry ID of the block, e.g. {@code "minecraft:stone"}
 *                 or {@code "create:mechanical_bearing"}.
 * @param minCount Minimum number of this block required for successful assembly.
 *                 Use {@code -1} to disable the minimum check.
 * @param maxCount Maximum number of this block allowed per contraption.
 *                 Use {@code -1} to disable the maximum check.
 */
public record BlockLimitEntry(String blockId, int minCount, int maxCount) {

    /**
     * Attempts to parse a config string of the form {@code "blockId:min:max"}.
     *
     * @param raw The raw config value.
     * @return A valid {@link BlockLimitEntry}, or {@code null} if the value is
     *         malformed.
     */
    public static BlockLimitEntry parse(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String[] parts = raw.trim().split(":");
        // Acceptable formats:
        //   namespace:path:min:max   → 4 parts
        //   namespace:path:max       → 3 parts (min defaults to -1)
        // Note: block IDs always contain exactly one colon, so after splitting
        // we expect 3 or 4 segments in total.
        if (parts.length == 4) {
            // "namespace:path:min:max"
            String blockId = parts[0] + ":" + parts[1];
            int min = parseIntSafe(parts[2]);
            int max = parseIntSafe(parts[3]);
            if (min < -1 || max < -1) return null;
            return new BlockLimitEntry(blockId, min, max);
        } else if (parts.length == 3) {
            // "namespace:path:max" (backward-compat / shorthand)
            String blockId = parts[0] + ":" + parts[1];
            int max = parseIntSafe(parts[2]);
            if (max < -1) return null;
            return new BlockLimitEntry(blockId, -1, max);
        }
        return null;
    }

    /** Returns a human-readable representation of this rule. */
    @Override
    public String toString() {
        return blockId + " [min=" + (minCount < 0 ? "unlimited" : minCount)
                + ", max=" + (maxCount < 0 ? "unlimited" : maxCount) + "]";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE; // signals a parse error
        }
    }
}
