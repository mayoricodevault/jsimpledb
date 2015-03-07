
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.util;

import java.util.HashMap;

import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.Transaction;

/**
 * Cache for randomly generated object IDs based on unique strings.
 *
 * <p>
 * Instances are thread safe.
 * </p>
 *
 * @see XMLObjectSerializer
 */
public class GeneratedIdCache {

    private final HashMap<String, ObjId> map = new HashMap<>();

    /**
     * Generate an object ID for the given object type storage ID and string.
     *
     * <p>
     * If an {@link ObjId} has already been created for the specified {@code storageId} and {@code string} by this instance,
     * it will be returned.
     * Otherwise, a random {@link ObjId} for which no object exists in the specified {@link Transaction} is generated
     * and returned.
     * </p>
     *
     * @param tx transaction from which to allocate new object IDs
     * @param storageId object type storage ID
     * @param string unique string
     * @return unique, unallocated object ID corresponding to {@code storageId} and {@code string}
     * @throws IllegalArgumentException if {@code storageId} is invalid
     * @throws IllegalArgumentException if {@code tx} or {@code string} is null
     */
    public synchronized ObjId getGeneratedId(Transaction tx, int storageId, String string) {
        if (tx == null)
            throw new IllegalArgumentException("null tx");
        if (string == null)
            throw new IllegalArgumentException("null string");
        final String key = "" + storageId + ":" + string;
        ObjId id = this.map.get(key);
        if (id == null) {
            id = tx.generateId(storageId);
            this.map.put(key, id);
        }
        return id;
    }

    /**
     * Clear this instance.
     */
    public synchronized void clear() {
        this.map.clear();
    }
}

