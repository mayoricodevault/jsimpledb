
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.change;

import org.jsimpledb.JObject;
import org.jsimpledb.JTransaction;

/**
 * Change notification that indicates an object has been deleted.
 *
 * <p>
 * This type of change notification is never generated by JSimpleDB itself; object deletion notifications are instead
 * delivered to {@link org.jsimpledb.annotation.OnDelete &#64;OnDelete} methods, which do not take any parameters.
 * This class exists as a convenience for application code that may want to unify handling of
 * object change and object lifecycle events.
 * </p>
 *
 * @param <T> the type of the object that was deleted
 */
public class ObjectDelete<T> extends Change<T> {

    /**
     * Constructor.
     *
     * @param jobj Java model object that was deleted
     * @throws IllegalArgumentException if {@code jobj} is null
     */
    public ObjectDelete(T jobj) {
        super(jobj);
    }

    @Override
    public <R> R visit(ChangeSwitch<R> target) {
        return target.caseObjectDelete(this);
    }

    @Override
    public void apply(JTransaction jtx, JObject jobj) {
        jtx.delete(jobj);
    }

// Object

    @Override
    public String toString() {
        return "ObjectDelete[objId=" + ((JObject)this.getObject()).getObjId() + "]";
    }
}

