package com.gmalvestiti.minecraft.easyconfig.api.spi;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

/**
 * Copies config state without sharing mutable nested objects.
 *
 * <p>EasyConfig copies for update candidates, published async snapshots, grouped loads, and
 * {@code beforeSave} isolation. The default is a JSON round-trip; replace it when a
 * hand-written copy would be faster:
 *
 * <pre>{@code
 * public final class MyModConfigCloner implements StateCloner<MyModConfig> {
 *
 *     @Override
 *     public MyModConfig copy(MyModConfig source) {
 *         MyModConfig copy = new MyModConfig();
 *         copy.hudScale = source.hudScale;
 *         copy.hiddenHints = new ArrayList<>(source.hiddenHints); // copy, don't share
 *         return copy;
 *     }
 * }
 *
 * EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .stateCloner(new MyModConfigCloner())
 *     .create();
 * }</pre>
 *
 * <p>An implementation must preserve every persisted field: dropping one during copy corrupts
 * live state just as surely as dropping it during serialization. It must also be safe to call
 * from the holder's operation thread, since async holders copy while other threads read the
 * published state.
 *
 * <p>Meant to be implemented, and a functional interface — it will keep exactly one abstract
 * method. Anything added in a future 1.x release will be {@code default}.
 *
 * @param <T> config root type copied by this cloner
 */
@FunctionalInterface
public interface StateCloner<T> {

    /**
     * Creates an isolated deep copy of {@code source}.
     *
     * @param source state to copy; EasyConfig passes a non-null root instance
     * @return a distinct instance with no shared mutable children; never the same reference as
     *         {@code source}
     * @throws EasyConfigException when the copy
     *     cannot be produced and the failure should be handled by the active policy
     */
    T copy(T source);
}
