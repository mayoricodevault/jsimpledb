
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.parse.func;

import org.jsimpledb.parse.ParseContext;
import org.jsimpledb.parse.ParseSession;
import org.jsimpledb.parse.expr.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superclass of all {@link ParseSession} functions.
 *
 * @see Function
 */
public abstract class AbstractFunction {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final String name;

// Constructors

    /**
     * Constructor.
     *
     * @param name function name
     */
    protected AbstractFunction(String name) {
        if (name == null)
            throw new IllegalArgumentException("null name");
        this.name = name;
    }

// Accessors

    /**
     * Get the name of this function.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get function usage string. For example: {@code pow(base, exponent)}.
     */
    public abstract String getUsage();

    /**
     * Get summarized help (typically a single line).
     */
    public abstract String getHelpSummary();

    /**
     * Get expanded help (typically multiple lines). May refer to the {@linkplain #getUsage usage string}.
     *
     * <p>
     * The implementation in {@link AbstractFunction} delegates to {@link #getHelpSummary getHelpSummary()}.
     * </p>
     */
    public String getHelpDetail() {
        return this.getHelpSummary();
    }

// Parsing

    /**
     * Parse function parameters.
     *
     * <p>
     * The {@code ctx} will be pointing at the first parameter (if any) or closing parenthesis. This method should parse
     * (but not evaluate) function parameters up through the closing parenthesis.
     * </p>
     *
     * @param session parse session
     * @param ctx parse context
     * @param complete false if parse is "for real", true if only for tab completion calculation
     * @return parsed parameters object to be passed to {@link #apply apply()}
     * @throws ParseException if parse fails, or if {@code complete} is true and there are valid completions
     */
    public abstract Object parseParams(ParseSession session, ParseContext ctx, boolean complete);

    /**
     * Evaluate this function. There will be a transaction open.
     *
     * @param session parse session
     * @param params parsed parameters returned by {@link #parseParams parseParams()}
     * @throws RuntimeException if there is an error
     */
    public abstract Value apply(ParseSession session, Object params);
}
