package com.gmalvestiti.minecraft.template.platform.fabric;

//? if fabric {
import com.gmalvestiti.minecraft.template.TemplateCommon;
import net.fabricmc.api.ModInitializer;

public class FabricEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        TemplateCommon.init();
    }
}
//?}
