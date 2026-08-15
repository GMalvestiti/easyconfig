package com.gmalvestiti.minecraft.template.datagen.fabric;

//? if fabric {
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import com.gmalvestiti.minecraft.template.TemplateCommon;
import com.gmalvestiti.minecraft.template.datagen.fabric.provider.BiomeTagProviderFabric;
import com.gmalvestiti.minecraft.template.datagen.fabric.provider.EnglishLanguageProviderFabric;
import com.gmalvestiti.minecraft.template.datagen.fabric.provider.EntityTypeTagProviderFabric;

public class DataGeneratorFabric implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(EnglishLanguageProviderFabric::new);
        pack.addProvider(EntityTypeTagProviderFabric::new);
        pack.addProvider(BiomeTagProviderFabric::new);
    }

    @Override
    public String getEffectiveModId() {
        return TemplateCommon.MOD_ID;
    }
}
//?}
