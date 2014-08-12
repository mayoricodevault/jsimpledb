
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.change;

import org.jsimpledb.JObject;
import org.jsimpledb.JTransaction;

/**
 * Change notification that indicates a new object has been created.
 *
 * <p>
 * This type of change notification is never generated by JSimpleDB itself; object creation notifications are instead
 * delivered to {@link org.jsimpledb.annotation.OnCreate &#64;OnCreate} methods, which do not take any parameters.
 * This class exists as a convenience for application code that may want to unify handling of
 * object change and object lifecycle events.
 * </p>
 *
 * @param <T> the type of the object that was created
 */
public class ObjectCreate<T> extends Change<T> {

    /**
     * Constructor.
     *
     * @param jobj Java model object that was created
     * @throws IllegalArgumentException if {@code jobj} is null
     */
    public ObjectCreate(T jobj) {
        super(jobj);
    }

    @Override
    public <R> R visit(ChangeSwitch<R> target) {
        return target.caseObjectCreate(this);
    }

    @Override
    public void apply(JTransaction jtx, JObject jobj) {
        jtx.recreate(jobj);
    }

// Object

    @Override
    public String toString() {
        return "ObjectCreate[object=" + this.getObject() + "]";
    }
}

