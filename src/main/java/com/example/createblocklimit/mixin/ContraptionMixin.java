package com.example.createblocklimit.mixin;

import com.example.createblocklimit.CreateBlockLimitAddon;
import com.example.createblocklimit.config.BlockLimitConfig;
import com.example.createblocklimit.config.BlockLimitEntry;
import com.simibubi.create.content.contraptions.AssemblyException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
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
 * Mixin targeting {@code com.simibubi.create.content.contraptions.Contraption}.
 *
 * <h2>Why searchMovedStructure and not assemble()?</h2>
 * In Create 6.x / 1.21.1 the base {@code Contraption.assemble()} is declared
 * {@code abstract} — there is no bytecode body to inject into.  All concrete
 * subclasses (BearingContraption, PistonContraption, …) call
 * {@code searchMovedStructure()} to collect blocks, so that concrete method is
 * the single reliable injection point that covers every contraption type.
 *
 * <h2>Injection point</h2>
 * We inject at the RETURN of {@code searchMovedStructure()}.  At that moment
 * {@link #blocks} is fully populated, allowing an accurate block count.
 * If any configured rule is violated we:
 * <ol>
 *   <li>Set the return value to {@code false} so the caller treats assembly
 *       as failed.</li>
 *   <li>Throw {@link AssemblyException} via sneaky-throw so Create's existing
 *       error-display logic shows a descriptive chat message to the player.</li>
 * </ol>
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption",
       remap = false)
public abstract class ContraptionMixin {

    // ── Shadow fields from Contraption ───────────────────────────────────────

    @Shadow(remap = false)
    protected Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks;

    // ── Injection ────────────────────────────────────────────────────────────

    /**
     * Injected at the RETURN of
     * {@code Contraption.searchMovedStructure(Level, BlockPos, Direction)}.
     *
     * <p>{@code searchMovedStructure} is concrete in {@code Contraption} and is
     * invoked by every subclass {@code assemble()} implementation to gather the
     * contraption's blocks.  This makes it the single correct injection point
     * for validating the fully-assembled block set.</p>
     */
    @Inject(
            method = "searchMovedStructure(Lnet/minecraft/world/level/Level;" +
                     "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void cblimit$validateBlockLimits(
            Level world,
            BlockPos pos,
            @Nullable Direction forcedDirection,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // Skip if structure search already failed.
        if (Boolean.FALSE.equals(cir.getReturnValue())) return;

        List<BlockLimitEntry> limits = BlockLimitConfig.getParsedLimits();
        if (limits.isEmpty()) return; // Nothing configured — fast-path exit.

        // ── Count every block type in the collected structure ─────────────────
        Map<String, Integer> blockCounts = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : blocks.values()) {
            String id = BuiltInRegistries.BLOCK
                    .getKey(info.state().getBlock())
                    .toString();
            blockCounts.merge(id, 1, Integer::sum);
        }

        // ── Validate each configured rule ─────────────────────────────────────
        List<String> violations = new ArrayList<>();

        for (BlockLimitEntry entry : limits) {
            // Use getOptional — get() returns Blocks.AIR for unknown IDs,
            // which would cause a false "Not enough Air" violation.
            var optBlock = BuiltInRegistries.BLOCK.getOptional(
                    net.minecraft.resources.ResourceLocation.parse(entry.blockId()));

            if (optBlock.isEmpty()) {
                CreateBlockLimitAddon.LOGGER.warn(
                        "[CreateBlockLimit] Unknown block in config: \"{}\" — skipping.",
                        entry.blockId());
                continue;
            }
            Block block = optBlock.get();

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

        if (violations.isEmpty()) return; // All rules satisfied.

        // ── Block assembly ────────────────────────────────────────────────────
        String message = String.join("\n", violations);

        CreateBlockLimitAddon.LOGGER.info(
                "[CreateBlockLimit] Assembly blocked at {} — {}",
                pos, message.replace("\n", " | "));

        blocks.clear();
        cir.setReturnValue(false);

        // Throw AssemblyException so Create shows the player a chat error.
        sneakyThrow(new AssemblyException(Component.literal(message)));
    }

    // ── Sneaky-throw helper ───────────────────────────────────────────────────

    /**
     * Throws {@code t} as if it were unchecked.
     * Safe here because {@code searchMovedStructure} already declares
     * {@code throws AssemblyException} and the JVM enforces no compile-time
     * checked-exception restrictions.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
