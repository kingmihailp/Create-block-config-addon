package com.example.createblocklimit;

import com.example.createblocklimit.config.BlockLimitConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Create Block Config Addon.
 *
 * <p>This mod intercepts Create contraption assembly and enforces per-block
 * limits defined in the server configuration file.</p>
 *
 * <p>The actual interception logic lives in
 * {@link com.example.createblocklimit.mixin.AbstractContraptionMixin}.</p>
 */
@Mod(CreateBlockLimitAddon.MOD_ID)
public class CreateBlockLimitAddon {

    public static final String MOD_ID = "createblocklimit";

    /**
     * Shared logger — also used by the Mixin and config classes so that all
     * messages are grouped under the same prefix in the server log.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CreateBlockLimitAddon(IEventBus modEventBus, ModContainer container) {
        // Register the server config.  The file ends up at:
        //   <world>/serverconfig/createblocklimit-server.toml
        container.registerConfig(ModConfig.Type.SERVER, BlockLimitConfig.SERVER_SPEC);

        LOGGER.info("[CreateBlockLimit] Mod loaded. Block-limit rules will be "
                + "enforced during contraption assembly.");
    }
}
