package com.gmalvestiti.minecraft.template.datagen.fabric.provider;

//? if fabric {
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class EntityTypeTagProviderFabric extends FabricTagsProvider./*? if <26.1 {*//*EntityTypeTagProvider*//*?} else {*/EntityTypeTagsProvider/*?}*/ {

    public EntityTypeTagProviderFabric(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider registries) {

    }
}
//?}
