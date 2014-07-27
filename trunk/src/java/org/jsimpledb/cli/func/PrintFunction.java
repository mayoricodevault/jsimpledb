
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.cli.func;

import org.jsimpledb.cli.CliSession;
import org.jsimpledb.parse.expr.Value;
import org.jsimpledb.parse.func.Function;

@Function
public class PrintFunction extends SimpleCliFunction {

    public PrintFunction() {
        super("print", 1, 1);
    }

    @Override
    public String getHelpSummary() {
        return "prints a value followed by newline";
    }

    @Override
    public String getUsage() {
        return "print(expr)";
    }

    @Override
    protected Value apply(CliSession session, Value[] params) {
        session.getWriter().println(params[0].get(session));
        return Value.NO_VALUE;
    }
}

