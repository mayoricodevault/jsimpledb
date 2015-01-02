
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import org.jsimpledb.annotation.JField;
import org.jsimpledb.annotation.JSimpleClass;
import org.jsimpledb.index.Index;
import org.testng.annotations.Test;

public class RawTypeTest extends TestSupport {

    @Test
    public void testRawType() throws Exception {
        final JSimpleDB jdb = BasicTest.getJSimpleDB(Widget.class);
        final JTransaction jtx = jdb.createTransaction(true, ValidationMode.MANUAL);
        JTransaction.setCurrent(jtx);
        try {
            AbstractData.queryByName();
        } finally {
            JTransaction.setCurrent(null);
        }
    }

// Model Classes

    @JSimpleClass(storageId = 100)
    public abstract static class AbstractData<T extends AbstractData<T>> implements JObject {

        @JField(storageId = 201, indexed = true)
        public abstract String getName();
        public abstract void setName(String name);

        @SuppressWarnings("rawtypes")
        public static Index<String, AbstractData> queryByName() {
            return JTransaction.getCurrent().querySimpleField(AbstractData.class, "name", String.class);
        }
    }

    @JSimpleClass(storageId = 400)
    public abstract static class Widget extends AbstractData<Widget> {
    }
}

