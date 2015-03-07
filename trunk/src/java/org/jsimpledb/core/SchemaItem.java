
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

/**
 * Superclass for the {@link ObjType} and {@link Field} classes which make up a {@link Schema} version.
 *
 * <p>
 * Instances have a {@linkplain #getStorageId storage ID} which must be unique across the {@link Schema} version.
 * Instances also have a {@linkplain #getName name} which must be a {@linkplain #NAME_PATTERN valid Java identifier}.
 * Instances are also associated with a {@linkplain #getSchema specific} {@link Schema}.
 * </p>
 */
public abstract class SchemaItem {

    /**
     * The regular expression that all {@link SchemaItem} names must match. This pattern is the same as is required
     * for Java identifiers.
     */
    public static final String NAME_PATTERN = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

    final String name;
    final int storageId;
    final Schema schema;

    SchemaItem(String name, int storageId, Schema schema) {
        if (name == null)
            throw new IllegalArgumentException("invalid null name");
        if (!name.matches(NAME_PATTERN))
            throw new IllegalArgumentException("invalid name `" + name + "'");
        if (storageId <= 0)
            throw new IllegalArgumentException("invalid storageId " + storageId);
        if (schema == null)
            throw new IllegalArgumentException("null schema");
        this.name = name;
        this.storageId = storageId;
        this.schema = schema;
    }

    /**
     * Get the associated with this instance.
     *
     * @return name of this object type or field, never null
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the storage ID associated with this instance.
     *
     * @return storage ID, always greater than zero
     */
    public int getStorageId() {
        return this.storageId;
    }

    /**
     * Get the schema version with which this instance is associated.
     *
     * @return associated schema version
     */
    public Schema getSchema() {
        return this.schema;
    }

    /**
     * Create corresponding {@link StorageInfo} object.
     */
    abstract StorageInfo toStorageInfo();

    @Override
    public abstract String toString();

    static Class<? extends FieldStorageInfo> infoTypeFor(Class<? extends Field<?>> type) {
        if (ReferenceField.class.isAssignableFrom(type))
            return ReferenceFieldStorageInfo.class;
        if (SimpleField.class.isAssignableFrom(type))
            return SimpleFieldStorageInfo.class;
        if (MapField.class.isAssignableFrom(type))
            return MapFieldStorageInfo.class;
        if (ListField.class.isAssignableFrom(type))
            return ListFieldStorageInfo.class;
        if (SetField.class.isAssignableFrom(type))
            return SetFieldStorageInfo.class;
        throw new IllegalArgumentException("no StorageInfo type known for " + type);
    }
}

