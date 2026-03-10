package com.example.createblocklimit.config;

import com.example.createblocklimit.CreateBlockLimitAddon;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge server-side configuration for the Create Block Config Addon.
 *
 * <p>The config file is placed in {@code <world>/serverconfig/createblocklimit-server.toml}
 * and is therefore per-world, which is ideal for server administrators.
 *
 * <h2>Config format</h2>
 * Each entry in {@code blockLimits} follows the pattern:
 * <pre>
 *   "namespace:path:minCount:maxCount"
 * </pre>
 * or the shorthand (min defaults to -1 = no minimum):
 * <pre>
 *   "namespace:path:maxCount"
 * </pre>
 * <ul>
 *   <li>Use {@code -1} for {@code minCount} or {@code maxCount} to disable
 *       the respective check.</li>
 *   <li>{@code 0} for {@code minCount} means the block is optional (no
 *       minimum), effectively the same as {@code -1}.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>
 * blockLimits = [
 *   "create:mechanical_bearing:1:1",   # exactly 1 bearing required
 *   "create:drill:0:4",                 # at most 4 drills
 *   "minecraft:tnt:-1:0",               # TNT is completely forbidden
 *   "create:rope_pulley:1:-1",          # at least 1 rope pulley required
 * ]
 * </pre>
 */
public final class BlockLimitConfig {

    // ── Config spec ──────────────────────────────────────────────────────────

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_LIMITS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "======================================================",
                "  Create Block Config Addon — Server Configuration",
                "======================================================",
                "",
                "Controls how many of a specific block may (or must) be",
                "present in a Create contraption for assembly to succeed.",
                "",
                "Entry format:  \"namespace:path:minCount:maxCount\"",
                "  - Use -1 to disable a check (e.g. no maximum).",
                "  - Shorthand \"namespace:path:maxCount\" sets only a max.",
                "",
                "Examples:",
                "  \"create:mechanical_bearing:1:1\"  -> exactly 1 bearing",
                "  \"create:drill:0:4\"               -> at most 4 drills",
                "  \"minecraft:tnt:-1:0\"             -> TNT forbidden",
                "  \"create:rope_pulley:1:-1\"        -> at least 1 rope pulley"
        );

        BLOCK_LIMITS = builder
                .comment("List of block-limit rules applied to every contraption assembly.")
                .defineListAllowEmpty(
                        "blockLimits",
                        List.of(
                                // Provide a few commented-out examples as the default
                                // (all checks disabled, so no restrictions by default)
                        ),
                        BlockLimitConfig::isValidEntry
                );

        SERVER_SPEC = builder.build();
    }

    // ── Parsed cache ─────────────────────────────────────────────────────────

    /**
     * Returns the currently active list of parsed {@link BlockLimitEntry} rules.
     *
     * <p>Invalid / unparseable entries are skipped and logged as warnings.</p>
     */
    public static List<BlockLimitEntry> getParsedLimits() {
        List<BlockLimitEntry> result = new ArrayList<>();

        for (String raw : BLOCK_LIMITS.get()) {
            BlockLimitEntry entry = BlockLimitEntry.parse(raw);
            if (entry == null) {
                CreateBlockLimitAddon.LOGGER.warn(
                        "[CreateBlockLimit] Skipping invalid config entry: \"{}\"", raw);
            } else {
                result.add(entry);
            }
        }

        return result;
    }

    // ── Validator used by ModConfigSpec ──────────────────────────────────────

    private static boolean isValidEntry(Object o) {
        if (!(o instanceof String s)) return false;
        return BlockLimitEntry.parse(s) != null;
    }

    // Prevent instantiation
    private BlockLimitConfig() {}
}
