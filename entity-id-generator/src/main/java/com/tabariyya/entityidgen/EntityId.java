package com.tabariyya.entityidgen;

import java.util.UUID;

/**
 * Implemented by every generated strongly typed entity identifier.
 *
 * <p>Generated identifiers are records named {@code <Entity>Id}, emitted into the same package as
 * the {@code @Entity} class they belong to. This interface deliberately lives in a separate,
 * constant package so that identifiers from any entity package share one supertype.
 */
public interface EntityId {

    /** The wrapped identifier value; never {@code null}. */
    UUID value();
}
