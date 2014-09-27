
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

/**
 * Thrown when an unknown field is accessed.
 */
@SuppressWarnings("serial")
public class UnknownFieldException extends DatabaseException {

    private final ObjType type;
    private final int storageId;

    /**
     * Constructor.
     *
     * @param type containing object type, or null for none
     * @param storageId unknown field storage ID
     * @param description description of the unknown field
     */
    public UnknownFieldException(ObjType type, int storageId, String description) {
        super(type + " has no " + description + " with storage ID " + storageId);
        this.type = type;
        this.storageId = storageId;
    }

    /**
     * Constructor.
     *
     * @param storageId unknown field storage ID
     * @param message exception message
     */
    public UnknownFieldException(int storageId, String message) {
        super(message);
        this.type = null;
        this.storageId = storageId;
    }

    /**
     * Get the object in which the field was not found, if any.
     *
     * @return containing object type, or null if this error is not specific to one object type
     */
    public ObjType getObjType() {
        return this.type;
    }

    /**
     * Get the storage ID that was not recognized.
     */
    public int getStorageId() {
        return this.storageId;
    }
}

