package com.gmalvestiti.minecraft.template.datagen.neoforge;

//? if neoforge {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//? if <1.21.4 {
/^import net.neoforged.neoforge.common.data.ExistingFileHelper;
^///?}
import net.neoforged.neoforge.data.event.GatherDataEvent;
import com.gmalvestiti.minecraft.template.TemplateCommon;
import com.gmalvestiti.minecraft.template.datagen.neoforge.provider.BiomeTagProviderNeoForge;
import com.gmalvestiti.minecraft.template.datagen.neoforge.provider.EnglishLanguageProviderNeoForge;
import com.gmalvestiti.minecraft.template.datagen.neoforge.provider.EntityTypeTagProviderNeoForge;

import java.util.concurrent.CompletableFuture;

//? if <1.21.4 {
/^@EventBusSubscriber(modid = TemplateCommon.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
^///?} else
@EventBusSubscriber(modid = TemplateCommon.MOD_ID)
public class DataGeneratorNeoForge {

    //? if <1.21.4 {
    /^@SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(
            event.includeClient(),
            new EnglishLanguageProviderNeoForge(output)
        );

        generator.addProvider(
            event.includeServer(),
            new BiomeTagProviderNeoForge(output, lookupProvider, existingFileHelper)
        );

        generator.addProvider(
            event.includeServer(),
            new EntityTypeTagProviderNeoForge(output, lookupProvider, existingFileHelper)
        );
    }
    ^///?} else {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        event.addProvider(new EnglishLanguageProviderNeoForge(output));
        event.addProvider(new EntityTypeTagProviderNeoForge(output, lookupProvider));
        event.addProvider(new BiomeTagProviderNeoForge(output, lookupProvider));
    }
    //?}
}
*///?}
