
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.parse.util;

import org.jsimpledb.parse.expr.EvalException;
import org.jsimpledb.util.CastFunction;

/**
 * Parsing specific subclass of {@link CastFunction} that throws {@link EvalException}s instead of {@link ClassCastException}s.
 */
public class ParseCastFunction<T> extends CastFunction<T> {

    public ParseCastFunction(Class<T> type) {
        super(type);
    }

    @Override
    protected RuntimeException handleFailure(Object obj, ClassCastException e) {
        return new EvalException("can't cast object of type " + obj.getClass().getName() + " to " + this.type.getName());
    }
}

