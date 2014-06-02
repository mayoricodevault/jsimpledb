
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Contains JSimpleDB library version information.
 */
public final class Version {

    /**
     * The version of this library.
     */
    public static final String JSIMPLEDB_VERSION;

    private static final String PROPERTIES_RESOURCE = "/jsimpledb.properties";
    private static final String VERSION_PROPERTY_NAME = "jsimpledb.version";

    static {
        Properties properties = new Properties();
        InputStream input = Version.class.getResourceAsStream(PROPERTIES_RESOURCE);
        if (input == null)
            throw new RuntimeException("can't find resource " + PROPERTIES_RESOURCE);
        try {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("unexpected exception", e);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                // ignore
            }
        }
        JSIMPLEDB_VERSION = properties.getProperty(VERSION_PROPERTY_NAME, "?");
    }

    private Version() {
    }
}

