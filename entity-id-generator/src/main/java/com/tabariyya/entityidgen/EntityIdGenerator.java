package com.tabariyya.entityidgen;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;
import org.hibernate.generator.GeneratorCreationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Produces a random id of whatever generated id type the annotated member declares.
 *
 * <p>One generator serves every id type: the concrete type is read from the annotated member at
 * bootstrap, so adding an entity needs no new generator. Values are created on insert only, matching
 * what {@code @GeneratedValue} did for the raw {@code UUID} columns.
 */
public class EntityIdGenerator implements BeforeExecutionGenerator {

    private final Constructor<?> idConstructor;

    public EntityIdGenerator(GeneratedEntityId config, Member idMember, GeneratorCreationContext context) {
        Class<?> idType = typeOf(idMember);
        if (!EntityId.class.isAssignableFrom(idType)) {
            throw new HibernateException("@GeneratedEntityId requires an " + EntityId.class.getSimpleName()
                    + " type, but " + idMember.getName() + " is " + idType.getName());
        }
        try {
            this.idConstructor = idType.getConstructor(UUID.class);
        } catch (NoSuchMethodException e) {
            throw new HibernateException(idType.getName() + " has no (UUID) constructor", e);
        }
    }

    private static Class<?> typeOf(Member member) {
        if (member instanceof Field field) {
            return field.getType();
        }
        if (member instanceof Method method) {
            return method.getReturnType();
        }
        throw new HibernateException("Unsupported id member: " + member);
    }

    @Override
    public Object generate(
            SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        // An explicitly assigned id wins, so callers can still control the value.
        if (currentValue != null) {
            return currentValue;
        }
        try {
            return idConstructor.newInstance(UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new HibernateException(
                    "Failed to generate " + idConstructor.getDeclaringClass().getName(), e);
        }
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
