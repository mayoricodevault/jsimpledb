
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import com.google.common.collect.Sets;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.NavigableSet;

import org.jsimpledb.kv.simple.SimpleKVDatabase;
import org.jsimpledb.schema.SchemaModel;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VersionChangeListenerTest extends TestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testVersionChangeListener() throws Exception {

        final SimpleKVDatabase kvstore = new SimpleKVDatabase(100, 200);

        final SchemaModel schema1 = SchemaModel.fromXML(new ByteArrayInputStream((
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<Schema>\n"
          + "  <Object name=\"Foo\" storageId=\"100\">\n"
          + "    <SimpleField name=\"i\" type=\"int\" storageId=\"101\"/>\n"
          + "  </Object>\n"
          + "</Schema>\n"
          ).getBytes("UTF-8")));

        final SchemaModel schema2 = SchemaModel.fromXML(new ByteArrayInputStream((
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<Schema>\n"
          + "  <Object name=\"Foo\" storageId=\"100\">\n"
          + "    <SimpleField name=\"i\" type=\"int\" storageId=\"101\"/>\n"
          + "    <SimpleField name=\"s\" type=\"java.lang.String\" storageId=\"102\"/>\n"
          + "    <SetField name=\"set\" storageId=\"103\">\n"
          + "        <SimpleField type=\"int\" storageId=\"104\"/>\n"
          + "    </SetField>"
          + "  </Object>\n"
          + "</Schema>\n"
          ).getBytes("UTF-8")));

        final SchemaModel schema3 = SchemaModel.fromXML(new ByteArrayInputStream((
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<Schema>\n"
          + "  <Object name=\"Foo\" storageId=\"100\">\n"
          + "    <SimpleField name=\"s\" type=\"java.lang.String\" storageId=\"102\"/>\n"
          + "  </Object>\n"
          + "</Schema>\n"
          ).getBytes("UTF-8")));

        final JSimpleDB db = new JSimpleDB(kvstore);

    // Tx #1

        Transaction tx = db.createTransaction(schema1, 1, true);

        ObjId id1 = tx.create(100);
        tx.writeSimpleField(id1, 101, 100);
        Assert.assertEquals(tx.getSchemaVersion(id1), 1);

        tx.commit();

    // Tx #2

        tx = db.createTransaction(schema2, 2, true);

        final boolean[] notified = new boolean[1];
        tx.addVersionChangeListener(new VersionChangeListener() {
            @Override
            public void onVersionChange(Transaction tx, ObjId id,
              int oldVersion, int newVersion, Map<Integer, Object> oldFieldValues) {
                Assert.assertEquals(oldVersion, 1);
                Assert.assertEquals(newVersion, 2);
                Assert.assertEquals(oldFieldValues.keySet(), Sets.newHashSet(101));
                Assert.assertEquals(oldFieldValues.get(101), 100);
                notified[0] = true;
            }
        });

        Assert.assertEquals(tx.getSchemaVersion(id1), 1);
        Assert.assertFalse(notified[0]);

        Assert.assertEquals(tx.readSimpleField(id1, 101, false), 100);
        Assert.assertEquals(tx.getSchemaVersion(id1), 1);
        Assert.assertFalse(notified[0]);

        Assert.assertEquals(tx.readSimpleField(id1, 101), 100);
        Assert.assertEquals(tx.getSchemaVersion(id1), 2);
        Assert.assertTrue(notified[0]);

        Assert.assertFalse(tx.updateSchemaVersion(id1));
        Assert.assertEquals(tx.getSchemaVersion(id1), 2);
        Assert.assertTrue(notified[0]);

        tx.writeSimpleField(id1, 102, "foobar");

        NavigableSet<Integer> set = (NavigableSet<Integer>)tx.readSetField(id1, 103);
        Assert.assertTrue(set.isEmpty());

        set.add(123);
        set.add(456);
        set.add(789);

        Assert.assertEquals(set, Sets.newHashSet(123, 456, 789));

        tx.commit();

    // Tx #3

        tx = db.createTransaction(schema3, 3, true);

        notified[0] = false;
        tx.addVersionChangeListener(new VersionChangeListener() {
            @Override
            public void onVersionChange(Transaction tx, ObjId id,
              int oldVersion, int newVersion, Map<Integer, Object> oldFieldValues) {
                Assert.assertEquals(oldVersion, 2);
                Assert.assertEquals(newVersion, 3);
                Assert.assertEquals(oldFieldValues.keySet(), Sets.newHashSet(101, 102, 103));
                Assert.assertEquals(oldFieldValues.get(101), 100);
                Assert.assertEquals(oldFieldValues.get(102), "foobar");
                Assert.assertEquals(oldFieldValues.get(103), Sets.<Integer>newHashSet(123, 456, 789));
                notified[0] = true;
            }
        });

        Assert.assertEquals(tx.getSchemaVersion(id1), 2);
        Assert.assertFalse(notified[0]);

        set = (NavigableSet<Integer>)tx.readSetField(id1, 103, false);
        Assert.assertEquals(tx.getSchemaVersion(id1), 2);
        Assert.assertFalse(notified[0]);

        Assert.assertEquals(set, Sets.newHashSet(123, 456, 789));

        Assert.assertTrue(tx.updateSchemaVersion(id1));
        Assert.assertEquals(tx.getSchemaVersion(id1), 3);
        Assert.assertTrue(notified[0]);

        Assert.assertEquals(set, Sets.<Integer>newHashSet());

        Assert.assertFalse(tx.updateSchemaVersion(id1));

        Assert.assertTrue(notified[0]);
        Assert.assertEquals(tx.getSchemaVersion(id1), 3);

        tx.commit();

    }
}

