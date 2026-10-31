package dev.geo.sockschunkfix;

import net.fabricmc.api.ModInitializer;

public final class SocksChunkFix implements ModInitializer {
    public static final String MOD_ID = "socks_chunkfix";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SocksChunkFix");

    @Override
    public void onInitialize() {
        SelfTestCommand.register();
        LOGGER.info("Registry relax + lookup cache active.");
    }
}
