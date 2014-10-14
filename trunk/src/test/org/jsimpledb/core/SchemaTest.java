
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

import java.io.ByteArrayInputStream;

import org.jsimpledb.TestSupport;
import org.jsimpledb.kv.simple.SimpleKVDatabase;
import org.jsimpledb.schema.SchemaModel;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SchemaTest extends TestSupport {

    @Test(dataProvider = "cases")
    private void testSchema(boolean valid, String xml) throws Exception {
        xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Schema formatVersion=\"1\">\n" + xml + "</Schema>\n";
        final SimpleKVDatabase kvstore = new SimpleKVDatabase();
        final Database db = new Database(kvstore);

        // Validate XML
        final SchemaModel schema;
        try {
            schema = SchemaModel.fromXML(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        } catch (InvalidSchemaException e) {
            assert !valid : "XML was supposed to be valid: " + this.show(e);
            return;
        }

        // Validate schema
        try {
            db.validateSchema(schema);
            assert valid : "schema was supposed to be invalid";
        } catch (InvalidSchemaException e) {
            assert !valid : "schema was supposed to be valid: " + this.show(e);
        }
    }

    @Test(dataProvider = "upgradeCases")
    private void testUpgradeSchema(boolean valid, String xml1, String xml2) throws Exception {
        final SimpleKVDatabase kvstore = new SimpleKVDatabase();
        final Database db = new Database(kvstore);

        xml1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Schema formatVersion=\"1\">\n" + xml1 + "</Schema>\n";
        final SchemaModel schema1 = SchemaModel.fromXML(new ByteArrayInputStream(xml1.getBytes("UTF-8")));
        db.createTransaction(schema1, 1, true).commit();

        xml2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Schema formatVersion=\"1\">\n" + xml2 + "</Schema>\n";
        final SchemaModel schema2 = SchemaModel.fromXML(new ByteArrayInputStream(xml2.getBytes("UTF-8")));
        try {
            db.createTransaction(schema2, 2, true);
            assert valid : "upgrade schema was supposed to be invalid";
        } catch (InvalidSchemaException e) {
            assert !valid : "upgrade schema was supposed to be valid: " + this.show(e);
        }
    }

    @DataProvider(name = "cases")
    public Object[][] cases() {
        return new Object[][] {
          { true,
            ""
          },

          { false,
            "!@#$%^&"
          },

          { false,
            "<!-- test 1 -->\n"
          + "<ObjectType name=\"Foo\"/>\n"
          },

          { false,
            "<!-- test 2 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"0\"/>\n"
          },

          { false,
            "<!-- test 3 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"-123\"/>\n"
          },

          { true,
            "<!-- test 4 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\"/>\n"
          },

          { false,
            "<!-- test 5 -->\n"
          + "<ObjectType storageId=\"123\"/>\n"
          },

          // Don't allow duplicate object storage IDs
          { false,
            "<!-- test 6 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\"/>\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\"/>\n"
          },

          // Disallow duplicate object names
          { false,
            "<!-- test 7 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\"/>\n"
          + "<ObjectType name=\"Foo\" storageId=\"456\"/>\n"
          },

          { false,
            "<!-- test 8 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\">\n"
          + "  <SimpleField name=\"i\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 9 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\">\n"
          + "  <SimpleField type=\"int\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 10 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\">\n"
          + "  <SimpleField storageId=\"456\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 11 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\">\n"
          + "  <SimpleField name=\"i\" type=\"int\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 12 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"0\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 13 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"123\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"-456\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 14 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"10\"/>\n"
          + "</ObjectType>\n"
          },

          { true,
            "<!-- test 15 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"20\"/>\n"
          + "</ObjectType>\n"
          },

          // Don't allow duplicate field storage IDs
          { false,
            "<!-- test 16 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"aaa\" type=\"int\" storageId=\"2\"/>\n"
          + "  <SimpleField name=\"bbb\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          },

          // Disallow duplicate field names in the same object
          { false,
            "<!-- test 17 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"2\"/>\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"3\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 18 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <SimpleField name=\"i\" type=\"float\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          },

          // Allow duplicate fields in different objects
          { true,
            "<!-- test 19 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <ReferenceField name=\"i\" storageId=\"2\"/>\n"  // default onDelete is EXCEPTION
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <ReferenceField name=\"i\" storageId=\"2\" onDelete=\"NOTHING\"/>\n"
          + "</ObjectType>\n"
          },

          { true,
            "<!-- test 20 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 21 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" name=\"dummy\" storageId=\"21\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

          { true,
            "<!-- test 22 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"21\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"11\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"21\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

          { true,
            "<!-- test 22.5 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"21\" indexed=\"true\"/>\n"       // indexed
          + "  </SetField>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"11\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"21\" indexed=\"false\"/>\n"      // not indexed
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

            // Inconsistent sub-field storage ID
          { false,
            "<!-- test 23 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"21\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"22\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

            // Inconsistent super-field storage ID
          { false,
            "<!-- test 23.5 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField type=\"int\" storageId=\"22\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <SetField name=\"set\" storageId=\"21\">\n"
          + "    <SimpleField type=\"int\" storageId=\"22\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

          { true,
            "<!-- test 24 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <CounterField name=\"counter\" storageId=\"20\"/>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 25 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <CounterField name=\"counter\"/>\n"
          + "</ObjectType>\n"
          },

          // Counter fields cannot be sub-fields
          { false,
            "<!-- test 26 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <CounterField name=\"counter\" storageId=\"20\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 27 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <CounterField storageId=\"20\"/>\n"
          + "</ObjectType>\n"
          },

          // Allow duplicate field storage IDs in different objects
          { true,
            "<!-- test 28 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <SimpleField name=\"i\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          },

          // Invalid names
          { false,
            "<!-- test 29 -->\n"
          + "<ObjectType name=\" Foo\" storageId=\"10\"/>\n"
          },

          // Invalid names
          { false,
            "<!-- test 30 -->\n"
          + "<ObjectType name=\"\" storageId=\"10\"/>\n"
          },

          // Invalid names
          { false,
            "<!-- test 31 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SimpleField name=\"2foo\" type=\"int\" storageId=\"2\"/>\n"
          + "</ObjectType>\n"
          },

          // Sub-fields can have names but they must be the right ones
          { true,
            "<!-- test 32 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField name=\"element\" type=\"int\" storageId=\"22\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

          { true,
            "<!-- test 33 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <MapField name=\"map\" storageId=\"20\">\n"
          + "    <SimpleField name=\"key\" type=\"int\" storageId=\"22\"/>\n"
          + "    <SimpleField name=\"value\" type=\"int\" storageId=\"23\"/>\n"
          + "  </MapField>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 34 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <SetField name=\"set\" storageId=\"20\">\n"
          + "    <SimpleField name=\"item\" type=\"int\" storageId=\"22\"/>\n"
          + "  </SetField>\n"
          + "</ObjectType>\n"
          },

          { false,
            "<!-- test 35 -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <MapField name=\"map\" storageId=\"20\">\n"
          + "    <SimpleField name=\"KEY\" type=\"int\" storageId=\"22\"/>\n"
          + "    <SimpleField name=\"VALUE\" type=\"int\" storageId=\"23\"/>\n"
          + "  </MapField>\n"
          + "</ObjectType>\n"
          },

          // Missing name
          { false,
            "<!-- test 36 -->\n"
          + "<ObjectType storageId=\"10\"/>\n"
          },

        };
    }

    @DataProvider(name = "upgradeCases")
    public Object[][] upgradeCases() {
        return new Object[][] {

          { false,
            "<!-- test 1a -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <CounterField name=\"counter\" storageId=\"20\"/>\n"
          + "</ObjectType>\n",

            "<!-- test 1b -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <ReferenceField name=\"ref1\" storageId=\"20\"/>\n"
          + "</ObjectType>\n",
          },

          // Change reference field onDelete
          { true,
            "<!-- test 2a -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <ReferenceField name=\"ref1\" storageId=\"20\" onDelete=\"EXCEPTION\"/>\n"
          + "</ObjectType>\n",

            "<!-- test 2b -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <ReferenceField name=\"ref1\" storageId=\"20\" onDelete=\"UNREFERENCE\"/>\n"
          + "</ObjectType>\n",
          },

          // Move a field
          { true,
            "<!-- test 3a -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "  <ReferenceField name=\"ref1\" storageId=\"11\"/>\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "</ObjectType>\n",

            "<!-- test 3b -->\n"
          + "<ObjectType name=\"Foo\" storageId=\"10\">\n"
          + "</ObjectType>\n"
          + "<ObjectType name=\"Bar\" storageId=\"20\">\n"
          + "  <ReferenceField name=\"ref1\" storageId=\"11\"/>\n"
          + "</ObjectType>\n",
          },

        };
    }
}

