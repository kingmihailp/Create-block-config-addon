package com.example.createblocklimit.mixin;

import com.example.createblocklimit.CreateBlockLimitAddon;
import com.example.createblocklimit.config.BlockLimitConfig;
import com.example.createblocklimit.config.BlockLimitEntry;
import com.simibubi.create.content.contraptions.AssemblyException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mixin targeting {@code com.simibubi.create.content.contraptions.AbstractContraption}.
 *
 * <h2>What this does</h2>
 * After Create's own {@code assemble()} method successfully collects all
 * contraption blocks, this mixin validates the collected block counts against
 * the rules in {@link BlockLimitConfig}.  If any rule is violated the mixin:
 * <ol>
 *   <li>Clears the collected block set so the game treats assembly as failed.</li>
 *   <li>Throws an {@link AssemblyException} via a sneaky-throw so Create's
 *       existing error-display logic shows a meaningful chat message to the
 *       player who triggered assembly.</li>
 * </ol>
 *
 * <h2>Why sneaky-throw?</h2>
 * Java's checked-exception system is a <em>compile-time</em> restriction; the
 * JVM itself places no such constraint.  Since we are injecting bytecode into a
 * method that already declares {@code throws AssemblyException}, the JVM will
 * propagate the exception perfectly.  The generic helper below satisfies the
 * compiler without wrapping.
 *
 * <h2>Compatibility note</h2>
 * This mixin targets the internal Create class
 * {@code com.simibubi.create.content.contraptions.AbstractContraption}.
 * If a future Create update renames this class or its {@code assemble} method,
 * update the {@code targets} / {@code method} values accordingly.
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraption",
       remap = false)
public abstract class AbstractContraptionMixin {

    // ── Shadow fields from AbstractContraption ───────────────────────────────

    /**
     * The map of all blocks that have been collected into this contraption.
     * Keys are block positions; values are {@link StructureTemplate.StructureBlockInfo}
     * records containing the block state and optional NBT data.
     *
     * <p>In Minecraft 1.21.1 the inner record type was promoted to a
     * top-level class; if you see compile errors adjust the import to
     * {@code net.minecraft.world.level.levelgen.structure.templatesystem.StructureBlockInfo}.</p>
     */
    @Shadow(remap = false)
    public Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks;

    // ── Injection ────────────────────────────────────────────────────────────

    /**
     * Injected at the RETURN point of
     * {@code AbstractContraption.assemble(Level, BlockPos)}.
     *
     * <p>We check <em>after</em> successful assembly so that all of Create's
     * own validation has already passed and {@link #blocks} is fully populated.</p>
     */
    @Inject(
            method = "assemble(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void cblimit$validateBlockLimits(
            Level world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // If assembly already failed for Create's own reasons, skip our check.
        if (Boolean.FALSE.equals(cir.getReturnValue())) return;

        List<BlockLimitEntry> limits = BlockLimitConfig.getParsedLimits();
        if (limits.isEmpty()) return; // Nothing configured — fast-path exit.

        // ── Count blocks present in the contraption ──────────────────────────
        Map<String, Integer> blockCounts = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : blocks.values()) {
            String id = BuiltInRegistries.BLOCK
                    .getKey(info.state().getBlock())
                    .toString();
            blockCounts.merge(id, 1, Integer::sum);
        }

        // ── Check each configured rule ────────────────────────────────────────
        List<String> violations = new ArrayList<>();

        for (BlockLimitEntry entry : limits) {
            // Validate that the block ID is known so we can give a better error.
            Block block = BuiltInRegistries.BLOCK.get(
                    net.minecraft.resources.ResourceLocation.parse(entry.blockId()));

            if (block == null) {
                CreateBlockLimitAddon.LOGGER.warn(
                        "[CreateBlockLimit] Config references unknown block: \"{}\" — skipping.",
                        entry.blockId());
                continue;
            }

            int count = blockCounts.getOrDefault(entry.blockId(), 0);

            if (entry.maxCount() >= 0 && count > entry.maxCount()) {
                violations.add(Component.translatable(
                        "createblocklimit.assembly.too_many",
                        Component.translatable(block.getDescriptionId()),
                        count,
                        entry.maxCount()
                ).getString());
            } else if (entry.minCount() > 0 && count < entry.minCount()) {
                violations.add(Component.translatable(
                        "createblocklimit.assembly.too_few",
                        Component.translatable(block.getDescriptionId()),
                        count,
                        entry.minCount()
                ).getString());
            }
        }

        if (violations.isEmpty()) return; // All rules satisfied — allow assembly.

        // ── Cancel assembly ───────────────────────────────────────────────────
        String combinedMessage = String.join("\n", violations);

        CreateBlockLimitAddon.LOGGER.info(
                "[CreateBlockLimit] Contraption assembly blocked at {} — {}",
                pos, combinedMessage.replace("\n", " | "));

        // Clear blocks so Create's internals treat this as a failed assembly,
        // then throw AssemblyException so Create shows a proper chat message.
        blocks.clear();
        cir.setReturnValue(false);

        sneakyThrow(new AssemblyException(Component.literal(combinedMessage)));
    }

    // ── Sneaky-throw helper ───────────────────────────────────────────────────

    /**
     * Throws {@code t} as an unchecked exception at the JVM level.
     *
     * <p>This is safe because:
     * <ul>
     *   <li>The JVM's exception model does not enforce checked-exception
     *       constraints at runtime.</li>
     *   <li>Our injection target {@code assemble()} already declares
     *       {@code throws AssemblyException}, so the JVM allows propagation.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
