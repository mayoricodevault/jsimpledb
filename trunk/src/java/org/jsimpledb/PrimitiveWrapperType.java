
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

/**
 * Primitive wrapper type.
 */
class PrimitiveWrapperType<T> extends NullSafeType<T> {

    PrimitiveWrapperType(PrimitiveType<T> primitiveType) {
        super(primitiveType.primitive.getWrapperType().getName(), primitiveType);
    }
}

