package com.gmalvestiti.minecraft.template.datagen.fabric.provider;

//? if fabric {
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class BiomeTagProviderFabric extends FabricTagsProvider<Biome> {

    public BiomeTagProviderFabric(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, Registries.BIOME, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider registries) {

    }
}
//?}
