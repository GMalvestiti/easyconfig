package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigObjectFactory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ConfigGroupLayout<T> implements ConfigLayout<T> {

    private final Class<T> type;
    private final ConfigLayoutContext layoutContext;

    public ConfigGroupLayout(Class<T> type, ConfigLayoutContext layoutContext) {
        this.type = type;
        this.layoutContext = layoutContext;
    }

    @Override
    public T createDefaults() {
        T defaults = newGroup();

        for (Field member : members()) {
            if (layoutContext.fieldAccess().read(member, defaults) == null) {
                layoutContext.fieldAccess().write(member, defaults, newMember(member));
            }
        }

        return defaults;
    }

    @Override
    public T load(Supplier<T> currentState) {
        T target = currentState.get();

        if (target == null) {
            target = newGroup();
        }

        for (Field member : members()) {
            Object loaded = readMember(member.getType());
            layoutContext.fieldAccess().write(member, target, loaded != null ? loaded : newMember(member));
        }

        return target;
    }

    @Override
    public void save(T data) {
        Map<Class<?>, Object> entries = new LinkedHashMap<>();

        for (Field member : members()) {
            entries.put(member.getType(), nonNullMember(data, member));
        }

        layoutContext.storage().writeAll(entries);
    }

    private List<Field> members() {
        return layoutContext.fieldAccess().configFieldsOf(type);
    }

    private <V> V readMember(Class<V> memberType) {
        return layoutContext.exceptionHandler()
            .onRead(memberType, () -> layoutContext.storage().read(memberType))
            .valueOr(() -> null);
    }

    private T newGroup() {
        return ConfigObjectFactory.newInstance(type, layoutContext.scope());
    }

    private Object newMember(Field member) {
        return ConfigObjectFactory.newInstance(member.getType(), layoutContext.scope());
    }

    private Object nonNullMember(T data, Field member) {
        Object value = layoutContext.fieldAccess().read(member, data);
        if (value != null) {
            return value;
        }
        Object replacement = newMember(member);
        layoutContext.fieldAccess().write(member, data, replacement);
        return replacement;
    }
}
