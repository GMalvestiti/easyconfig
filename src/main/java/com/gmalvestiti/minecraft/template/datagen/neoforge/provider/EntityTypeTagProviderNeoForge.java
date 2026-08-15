package com.gmalvestiti.minecraft.template.datagen.neoforge.provider;

//? if neoforge {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
//? if <1.21.4 {
/^import net.neoforged.neoforge.common.data.ExistingFileHelper;
^///?}
import com.gmalvestiti.minecraft.template.TemplateCommon;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class EntityTypeTagProviderNeoForge extends EntityTypeTagsProvider {

    //? if <1.21.4 {
    /^public EntityTypeTagProviderNeoForge(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, TemplateCommon.MOD_ID, existingFileHelper);
    }
    ^///?} else {
    public EntityTypeTagProviderNeoForge(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, TemplateCommon.MOD_ID);
    }
    //?}

    @Override
    protected void addTags(HolderLookup.@NonNull Provider registries) {

    }
}
*///?}
