package com.tabariyya.entityidgen;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Marks an {@code @Id} of a generated id type as assigned on insert.
 *
 * <p>Replaces {@code @GeneratedValue}, which cannot be used here: its AUTO strategy inspects the
 * field type to choose a generator, and for anything it does not recognise as UUID-like it falls
 * back to a sequence, then fails at insert looking for a table-specific sequence that does not
 * exist.
 */
@IdGeneratorType(EntityIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
public @interface GeneratedEntityId {}
