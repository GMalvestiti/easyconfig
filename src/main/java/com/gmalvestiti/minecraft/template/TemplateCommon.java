package com.gmalvestiti.minecraft.template;

import com.gmalvestiti.minecraft.template.config.TemplateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateCommon {

    public static final String MOD_ID = /*$ mod_id*/ "template";
    public static final Logger LOGGER = LoggerFactory.getLogger(TemplateCommon.MOD_ID);

    public static void init() {
        if (TemplateConfig.enabled) {
            TemplateCommon.info("Mod loaded.");
        } else {
            TemplateCommon.info("Mod disabled.");
        }
    }

    public static void info(String message) {
        TemplateCommon.LOGGER.info("[{}] {}", TemplateCommon.MOD_ID, message);
    }

    public static void error(String message, Exception e) {
        TemplateCommon.LOGGER.error("[{}] {}", TemplateCommon.MOD_ID, message, e);
    }
}
