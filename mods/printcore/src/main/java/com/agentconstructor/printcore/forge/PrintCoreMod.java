package com.agentconstructor.printcore.forge;

import com.agentconstructor.printcore.config.PrintCoreConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(PrintCoreMod.MODID)
public class PrintCoreMod {
    public static final String MODID = "printcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PrintCoreMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PrintCoreConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(ServerHooks.class);
    }
}
