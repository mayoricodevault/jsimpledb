
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.tuple;

/**
 * A tuple of two values.
 *
 * <p>
 * Instances are immutable.
 * </p>
 *
 * @param <V1> first value type
 * @param <V2> second value type
 */
public class Tuple2<V1, V2> extends AbstractHas2<V1, V2> {

    /**
     * Constructor.
     *
     * @param v1 the first value
     * @param v2 the second value
     */
    public Tuple2(V1 v1, V2 v2) {
        super(v1, v2);
    }
}

