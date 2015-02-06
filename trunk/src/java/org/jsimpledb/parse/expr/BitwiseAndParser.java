
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.parse.expr;

/**
 * Parses bit-wise AND expressions of the form {@code x & y}. Also supports {@link java.util.Set} intersection.
 */
public class BitwiseAndParser extends BinaryExprParser {

    public static final BitwiseAndParser INSTANCE = new BitwiseAndParser();

    public BitwiseAndParser() {
        super(EqualityParser.INSTANCE, Op.AND);
    }
}

