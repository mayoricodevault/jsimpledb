
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.jsimpledb.JSimpleDB;
import org.jsimpledb.JTransaction;
import org.jsimpledb.ValidationMode;
import org.jsimpledb.cli.cmd.Command;
import org.jsimpledb.cli.func.Function;
import org.jsimpledb.cli.parse.ParseException;
import org.jsimpledb.core.Database;
import org.jsimpledb.core.SchemaVersion;
import org.jsimpledb.core.Transaction;
import org.jsimpledb.schema.NameIndex;
import org.jsimpledb.schema.SchemaModel;

import jline.console.ConsoleReader;

/**
 * Represents one console session.
 */
public class Session {

    private final JSimpleDB jdb;
    private final Database db;
    private final ConsoleReader console;
    private final PrintWriter writer;
    private final LinkedHashSet<String> imports = new LinkedHashSet<>();
    private final TreeMap<String, Command> commands = new TreeMap<>();
    private final TreeMap<String, Function> functions = new TreeMap<>();
    private final TreeMap<String, Object> variables = new TreeMap<>();

    private Transaction tx;
    private SchemaModel schemaModel;
    private ValidationMode validationMode;
    private NameIndex nameIndex;
    private int schemaVersion;
    private boolean allowNewSchema;
    private boolean done;
    private boolean verbose;
    private boolean readOnly;
    private int lineLimit = 16;

// Constructors

    /**
     * Constructor for core level access only.
     */
    public Session(Database db, ConsoleReader console) {
        this.jdb = null;
        this.db = db;
        this.console = console;
        this.writer = new PrintWriter(console.getOutput(), true);
        this.imports.add("java.lang.*");
    }

    /**
     * Constructor for {@link JSimpleDB} level access.
     */
    public Session(JSimpleDB jdb, ConsoleReader console) {
        this.jdb = jdb;
        this.db = jdb.getDatabase();
        this.console = console;
        this.writer = new PrintWriter(console.getOutput(), true);
        this.imports.add("java.lang.*");
    }

// Accessors

    /**
     * Get the associated {@link JSimpleDB}, if any.
     *
     * @return the associated {@link JSimpleDB} or null if there is none
     */
    public JSimpleDB getJSimpleDB() {
        return this.jdb;
    }

    /**
     * Get the associated {@link Database}.
     *
     * @return the associated {@link Database}
     */
    public Database getDatabase() {
        return this.db;
    }

    public ConsoleReader getConsole() {
        return this.console;
    }

    public PrintWriter getWriter() {
        return this.writer;
    }

    public Set<String> getImports() {
        return this.imports;
    }

    public SortedMap<String, Command> getCommands() {
        return this.commands;
    }

    public SortedMap<String, Function> getFunctions() {
        return this.functions;
    }

    public SortedMap<String, Object> getVars() {
        return this.variables;
    }

    public Transaction getTransaction() {
        if (this.tx == null)
            throw new IllegalStateException("no transaction associated with session");
        return this.tx;
    }

    public SchemaModel getSchemaModel() {
        return this.schemaModel;
    }
    public void setSchemaModel(SchemaModel schemaModel) {
        this.schemaModel = schemaModel;
        this.nameIndex = this.schemaModel != null ? new NameIndex(this.schemaModel) : null;
    }

    public NameIndex getNameIndex() {
        return this.nameIndex != null ? this.nameIndex : new NameIndex(new SchemaModel());
    }

    public int getSchemaVersion() {
        return this.schemaVersion;
    }
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public ValidationMode getValidationMode() {
        return this.validationMode;
    }
    public void setValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
    }

    public int getLineLimit() {
        return this.lineLimit;
    }
    public void setLineLimit(int lineLimit) {
        this.lineLimit = lineLimit;
    }

    public boolean getAllowNewSchema() {
        return this.allowNewSchema;
    }
    public void setAllowNewSchema(boolean allowNewSchema) {
        this.allowNewSchema = allowNewSchema;
    }

    public boolean isVerbose() {
        return this.verbose;
    }
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isDone() {
        return this.done;
    }
    public void setDone(boolean done) {
        this.done = done;
    }

// Class name resolution

    public Class<?> resolveClass(final String name) {
        final int firstDot = name.indexOf('.');
        final String firstPart = firstDot != -1 ? name.substring(0, firstDot - 1) : name;
        final ArrayList<String> packages = new ArrayList<>(this.imports.size() + 1);
        packages.add(null);
        packages.addAll(this.imports);
        for (String pkg : packages) {

            // Get absolute class name
            String className;
            if (pkg == null)
                className = name;
            else if (pkg.endsWith(".*"))
                className = pkg.substring(0, pkg.length() - 1) + name;
            else {
                if (!firstPart.equals(pkg.substring(pkg.lastIndexOf('.') + 1, pkg.length() - 2)))
                    continue;
                className = pkg.substring(0, pkg.length() - 2 - firstPart.length()) + name;
            }

            // Try package vs. nested classes
            while (true) {
                try {
                    return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    // not found
                }
                final int lastDot = className.lastIndexOf('.');
                if (lastDot == -1)
                    break;
                className = className.substring(0, lastDot) + "$" + className.substring(lastDot + 1);
            }
        }
        return null;
    }

// Errors

    public void report(Exception e) {
        final String message = e.getLocalizedMessage();
        try {
            if (e instanceof ParseException && message != null)
                this.console.println("Error: " + message);
            else
                this.console.println("Error: " + e.getClass().getSimpleName() + (message != null ? ": " + message : ""));
            if (this.verbose || this.showStackTrace(e))
                e.printStackTrace(this.writer);
        } catch (IOException ioe) {
            this.setDone(true);
        }
    }

    protected boolean showStackTrace(Exception e) {
        return e instanceof NullPointerException || (e instanceof ParseException && e.getLocalizedMessage() == null);
    }

// Transactions

    public boolean perform(Action action) {
        try {
            final boolean newTransaction = this.tx == null;
            if (newTransaction) {
                if (!this.openTransaction())
                    return false;
            }
            boolean success = false;
            try {
                action.run(this);
                success = true;
            } finally {
                if (newTransaction && this.tx != null) {
                    if (success)
                        this.commitTransaction();
                    else
                        this.rollbackTransaction();
                }
            }
            return success;
        } catch (Exception e) {
            this.report(e);
            return false;
        }
    }

    public boolean openTransaction() {
        try {
            if (this.tx != null)
                throw new IllegalStateException("a transaction is already open");
            if (this.jdb != null) {
                boolean exists = true;
                try {
                    JTransaction.getCurrent();
                } catch (IllegalStateException e) {
                    exists = false;
                }
                if (exists)
                    throw new IllegalStateException("a transaction is already open");
                final JTransaction jtx = this.jdb.createTransaction(this.allowNewSchema,
                  validationMode != null ? validationMode : ValidationMode.AUTOMATIC);
                JTransaction.setCurrent(jtx);
                this.tx = jtx.getTransaction();
            } else
                this.tx = this.db.createTransaction(this.schemaModel, this.schemaVersion, this.allowNewSchema);
            final SchemaVersion version = this.tx.getSchemaVersion();
            this.setSchemaModel(version.getSchemaModel());
            this.setSchemaVersion(version.getVersionNumber());
            this.tx.setReadOnly(this.readOnly);
            return true;
        } catch (Exception e) {
            this.tx = null;
            this.report(e);
            return false;
        }
    }

    public boolean commitTransaction() {
        try {
            if (this.tx == null)
                throw new IllegalStateException("no transaction");
            if (this.jdb != null)
                JTransaction.getCurrent().commit();
            else
                this.tx.commit();
            return true;
        } catch (Exception e) {
            this.report(e);
            return false;
        } finally {
            this.tx = null;
            if (this.jdb != null)
                JTransaction.setCurrent(null);
        }
    }

    public boolean rollbackTransaction() {
        try {
            if (this.tx == null)
                throw new IllegalStateException("no transaction");
            if (this.jdb != null)
                JTransaction.getCurrent().rollback();
            else
                this.tx.rollback();
            return true;
        } catch (Exception e) {
            this.report(e);
            return false;
        } finally {
            this.tx = null;
            if (this.jdb != null)
                JTransaction.setCurrent(null);
        }
    }
}
