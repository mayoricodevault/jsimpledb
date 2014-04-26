
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

/**
 * Thrown by {@link Database#createTransaction} when the provided schema is invalid.
 */
@SuppressWarnings("serial")
public class InvalidSchemaException extends DatabaseException {

    InvalidSchemaException() {
    }

    public InvalidSchemaException(String message) {
        super(message);
    }

    public InvalidSchemaException(Throwable cause) {
        super(cause);
    }

    public InvalidSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}

