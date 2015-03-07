
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

import org.jsimpledb.util.ByteReader;
import org.jsimpledb.util.ByteWriter;
import org.jsimpledb.util.UnsignedIntEncoder;

/**
 * Per-object meta data encoded in the object's KV value.
 */
class ObjInfo {

    private static final int META_DATA_VERSION = 1;

    // Stored meta-data
    private final Transaction tx;
    private final ObjId id;
    private final int version;
    private final boolean deleteNotified;

    // Additional derived meta-data
    private Schema schema;
    private ObjType objType;

    ObjInfo(Transaction tx, ObjId id) {
        this.tx = tx;
        this.id = id;
        final byte[] value = tx.kvt.get(this.id.getBytes());
        if (value == null)
            throw new DeletedObjectException(this.id);
        final ByteReader reader = new ByteReader(value);
        final int metaDataVersion = UnsignedIntEncoder.read(reader);
        if (metaDataVersion != META_DATA_VERSION)
            throw new InconsistentDatabaseException("found unknown object meta-data version " + metaDataVersion + " in " + this.id);
        this.version = UnsignedIntEncoder.read(reader);
        if (this.version == 0)
            throw new InvalidObjectVersionException(this.id, this.version);
        this.deleteNotified = FieldTypeRegistry.BOOLEAN.read(reader);
    }

    public ObjId getId() {
        return this.id;
    }

    public int getStorageId() {
        return id.getStorageId();
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isDeleteNotified() {
        return this.deleteNotified;
    }

    public Schema getSchema() {
        if (this.schema == null) {
            try {
                this.schema = this.tx.schemas.getVersion(this.version);
            } catch (IllegalArgumentException e) {
                throw new InvalidObjectVersionException(id, this.version);
            }
        }
        return this.schema;
    }

    public ObjType getObjType() {
        if (this.objType == null) {
            try {
                this.objType = this.getSchema().getObjType(this.getStorageId());
            } catch (IllegalArgumentException e) {
                throw new InconsistentDatabaseException("object " + this.id + " has invalid storage ID", e);
            }
        }
        return this.objType;
    }

    public static void write(Transaction tx, ObjId id, int version, boolean deleteNotified) {
        final ByteWriter writer = new ByteWriter();
        UnsignedIntEncoder.write(writer, META_DATA_VERSION);
        UnsignedIntEncoder.write(writer, version);
        FieldTypeRegistry.BOOLEAN.write(writer, deleteNotified);
        tx.kvt.put(id.getBytes(), writer.getBytes());
    }
}

