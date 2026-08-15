package com.gmalvestiti.minecraft.easyconfig.storage.codec;

import com.gmalvestiti.minecraft.easyconfig.storage.ConfigBinder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.marhali.json5.Json5;
import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

final class Json5Codec implements ConfigCodec {

    static final ConfigCodec INSTANCE = new Json5Codec();

    private static final Json5 JSON5 = Json5.builder(options -> options
        .prettyPrinting()
        .indentFactor(2)
        .parseComments()
        .writeComments()
        .insertFinalNewline()
        .build());

    private Json5Codec() {}

    @Override
    public <V> V read(String text, Class<V> type) {
        return ConfigBinder.fromTree(toJsonTree(JSON5.parse(text)), type);
    }

    @Override
    public String write(Object data) {
        Class<?> type = data.getClass();
        Json5Element root = toJson5(ConfigBinder.toTree(data), type);
        comment(root, ConfigProperties.commentOfClass(type));
        try {
            return JSON5.serialize(root);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Json5Element toJson5(JsonElement element, Class<?> owner) {
        if (element.isJsonObject()) {
            Json5Object object = new Json5Object();
            for (Map.Entry<String, JsonElement> property : element.getAsJsonObject().entrySet()) {
                String key = property.getKey();
                Class<?> nested = ConfigProperties.nestedTypeOf(owner, key);
                Json5Element converted = toJson5(property.getValue(), nested);
                comment(converted, ConfigProperties.commentOfEntry(owner, key, nested));
                object.add(key, converted);
            }
            return object;
        }

        if (element.isJsonArray()) {
            Json5Array array = new Json5Array();
            for (JsonElement item : element.getAsJsonArray()) {
                array.add(toJson5(item, null));
            }
            return array;
        }

        if (element.isJsonNull()) {
            return Json5Primitive.fromNull();
        }

        return toJson5Primitive(element.getAsJsonPrimitive());
    }

    private Json5Element toJson5Primitive(JsonPrimitive primitive) {
        if (primitive.isBoolean()) {
            return Json5Primitive.fromBoolean(primitive.getAsBoolean());
        }

        if (primitive.isNumber()) {
            return Json5Primitive.fromNumber(primitive.getAsNumber());
        }

        return Json5Primitive.fromString(primitive.getAsString());
    }

    private JsonElement toJsonTree(Json5Element element) {
        if (element == null || element.isJson5Null()) {
            return JsonNull.INSTANCE;
        }

        if (element.isJson5Object()) {
            JsonObject object = new JsonObject();
            for (Map.Entry<String, Json5Element> property : element.getAsJson5Object().entrySet()) {
                object.add(property.getKey(), toJsonTree(property.getValue()));
            }
            return object;
        }

        if (element.isJson5Array()) {
            JsonArray array = new JsonArray();
            for (Json5Element item : element.getAsJson5Array()) {
                array.add(toJsonTree(item));
            }
            return array;
        }

        return toJsonPrimitive(element.getAsJson5Primitive());
    }

    private JsonElement toJsonPrimitive(Json5Primitive primitive) {
        if (primitive.isBoolean()) {
            return new JsonPrimitive(primitive.getAsBoolean());
        }

        if (primitive.isNumber()) {
            return new JsonPrimitive(primitive.getAsNumber());
        }

        if (primitive.isInstant()) {
            return new JsonPrimitive(primitive.getAsInstant().toString());
        }

        return new JsonPrimitive(primitive.getAsString());
    }

    private void comment(Json5Element element, String comment) {
        if (comment != null) {
            element.setComment(comment.replace("*/", "* /"));
        }
    }
}
