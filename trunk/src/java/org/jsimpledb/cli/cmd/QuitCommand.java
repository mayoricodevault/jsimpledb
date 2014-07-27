
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.cli.cmd;

import java.util.Map;

import org.jsimpledb.cli.CliSession;
import org.jsimpledb.parse.ParseContext;

@Command
public class QuitCommand extends AbstractCommand implements CliSession.Action {

    public QuitCommand() {
        super("quit");
    }

    @Override
    public String getHelpSummary() {
        return "Quits out of the JSimpleDB command line";
    }

    @Override
    public CliSession.Action getAction(CliSession session, ParseContext ctx, boolean complete, Map<String, Object> params) {
        return this;
    }

// CliSession.Action

    @Override
    public void run(CliSession session) throws Exception {
        session.setDone(true);
        session.getWriter().println("Bye");
    }
}

