
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

import java.util.Arrays;
import java.util.List;

import org.jsimpledb.kv.KeyFilter;

/**
 * Support superclass for the various core index classes.
 */
abstract class AbstractCoreIndex {

    final Transaction tx;
    final AbstractIndexView indexView;

// Constructors

    protected AbstractCoreIndex(Transaction tx, int size, AbstractIndexView indexView) {
        if (tx == null)
            throw new IllegalArgumentException("null tx");
        if (indexView == null)
            throw new IllegalArgumentException("null indexView");
        this.tx = tx;
        this.indexView = indexView;
        if (this.indexView.fieldTypes.length != size)
            throw new RuntimeException("internal error: indexView has the wrong size");
    }

// Methods

    /**
     * Get all of the {@link FieldType}s associated with this instance. The list includes an entry
     * for each indexed value type, followed by a final entry representing the index target type.
     *
     * @return unmodifiable list of field types
     */
    public List<FieldType<?>> getFieldTypes() {
        return Arrays.asList(this.indexView.fieldTypes.clone());
    }

    /**
     * Apply key filtering to field values at the specified index. This method works cummulatively: the new instance
     * filters to the intersection of the given key filter and any existing key filter on that field.
     *
     * @param index zero-based object type field offset
     * @param keyFilter key filtering to apply
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     * @throws IllegalArgumentException if {@code keyFilter} is null
     */
    public abstract AbstractCoreIndex filter(int index, KeyFilter keyFilter);
}

