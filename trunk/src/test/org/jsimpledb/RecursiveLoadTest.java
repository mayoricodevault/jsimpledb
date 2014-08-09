
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import org.jsimpledb.annotation.JField;
import org.jsimpledb.annotation.JSimpleClass;
import org.jsimpledb.annotation.OnChange;
import org.jsimpledb.change.SimpleFieldChange;
import org.testng.annotations.Test;

public class RecursiveLoadTest extends TestSupport {

    @Test
    public void testRecursiveLoad() {

        final JSimpleDB jdb = BasicTest.getJSimpleDB(Person.class);
        final JTransaction tx = jdb.createTransaction(true, ValidationMode.AUTOMATIC);
        JTransaction.setCurrent(tx);
        try {

            tx.create(Person.class);

            tx.commit();

        } finally {
            JTransaction.setCurrent(null);
        }
    }

// Model Classes

    @JSimpleClass(storageId = 100)
    public abstract static class Person implements JObject {

        protected Person() {
            this.setName("Some name");
        }

        @JField(storageId = 101)
        public abstract String getName();
        public abstract void setName(String name);

        @OnChange("name")
        private void nameChanged(SimpleFieldChange<Person, String> change) {
        }
    }
}

