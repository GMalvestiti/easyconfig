package com.gmalvestiti.minecraft.template.datagen.fabric.provider;

//? if fabric {
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class EnglishLanguageProviderFabric extends FabricLanguageProvider {

    public EnglishLanguageProviderFabric(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.@NonNull Provider registries, @NonNull TranslationBuilder translationBuilder) {

    }
}
//?}
