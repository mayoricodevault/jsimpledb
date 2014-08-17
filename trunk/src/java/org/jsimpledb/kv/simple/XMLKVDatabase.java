
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv.simple;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.stream.XMLStreamException;

import org.dellroad.stuff.io.AtomicUpdateFileOutputStream;
import org.dellroad.stuff.io.FileStreamRepository;
import org.dellroad.stuff.io.StreamRepository;
import org.jsimpledb.kv.KVDatabaseException;
import org.jsimpledb.kv.RetryTransactionException;
import org.jsimpledb.kv.util.XMLSerializer;

/**
 * Simple persistent {@link org.jsimpledb.kv.KVDatabase} backed by an XML file stored in a {@link StreamRepository}.
 * The data is kept in memory, and the XML file is rewritten in its entirety after each successful commit.
 * In normal usage, the XML file is stored in a regular {@link File} using a {@link FileStreamRepository}, which
 * guarantees (via the use of {@link AtomicUpdateFileOutputStream}) that a partially written XML file can never exist.
 *
 * <p>
 * If a {@link FileNotFoundException} is caught when trying to read the XML file, we assume that the underlying file has
 * not yet been created and the database will initially be empty. Alternately, you can configure a file containing
 * default initial content via {@link #setInitialContentFile setInitialContentFile()}, or override {@link #getInitialContent}
 * to create the initial content more dynamically.
 * </p>
 *
 * <p>
 * When a {@link FileStreamRepository} is used, instances support "out-of-band" updates of the XML file. In that case,
 * each time a transaction is accessed the modification timestamp of the XML file is examined. If the XML file has been
 * updated by some external process since the time the transaction was created, the database will be reloaded from
 * the XML file and the transaction will fail with a {@link RetryTransactionException}.
 * </p>
 *
 * <p>
 * Note that two different processes modifying the XML file at the same time is not without race conditions: e.g., it's possible
 * for an external process to update the XML file just as a transaction associated with this instance is being committed
 * and written to the file, which will result in overwriting the external process' changes.
 * </p>
 *
 * @see XMLSerializer
 * @see AtomicUpdateFileOutputStream
 */
public class XMLKVDatabase extends SimpleKVDatabase {

    private final StreamRepository repository;
    private final XMLSerializer serializer;
    private final File file;

    private int generation;
    private long timestamp;
    private File initialContentFile;

// Constructors

    /**
     * Normal constructor. Uses a {@link FileStreamRepository} backed by the specified file.
     *
     * @param file persistent XML file
     * @throws IllegalArgumentException if {@code file} is null
     */
    public XMLKVDatabase(File file) {
        this(new FileStreamRepository(file));
    }

    /**
     * Constructor allowing storage in any user-supplied {@link StreamRepository}.
     *
     * @param repository XML file storage
     * @throws IllegalArgumentException if {@code file} is null
     */
    public XMLKVDatabase(StreamRepository repository) {
        if (repository == null)
            throw new IllegalArgumentException("null repository");
        this.repository = repository;
        this.serializer = new XMLSerializer(this.kv);
        this.reload();
        this.file = repository instanceof FileStreamRepository ? ((FileStreamRepository)repository).getFile() : null;
    }

    /**
     * Get the initial content for an uninitialized database. This method is invoked when, on the first load,
     * the backing XML file is not found. It should return a stream that reads initial content for the database,
     * if any, otherwise null.
     *
     * <p>
     * The implementation in {@link XMLKVDatabase} opens and returns the {@link File} configured by
     * {@link #setInitialContentFile setInitialContentFile()}, if any.
     * </p>
     *
     * @return default initial XML database content, or null for none
     */
    protected InputStream getInitialContent() throws IOException {
        return this.initialContentFile != null ? new FileInputStream(this.initialContentFile) : null;
    }

    /**
     * Configure the {@link File} containing default initial content for an uninitialized database. This method is invoked
     * by {@link #getInitialContent} when, on the first load, the backing XML file is not found.
     *
     * @return default initial XML database file, or null for none
     */
    public void setInitialContentFile(File initialContentFile) {
        this.initialContentFile = initialContentFile;
    }

    @Override
    public synchronized XMLKVTransaction createTransaction() {
        this.checkForOutOfBandUpdate();
        return new XMLKVTransaction(this, this.getWaitTimeout(), this.generation);
    }

    /**
     * Forcibly reload this database by re-reading the XML file.
     *
     * <p>
     * Any transactions that are in-progress when this method is called immediately become unusable.
     * </p>
     */
    public synchronized void reload() {
        this.readXML();
    }

    /**
     * Get the generation number associated with the XML file.
     * The generation number is incremented every time the database is wholesale updated by reading the file into memory,
     * e.g., by invoking {@link #reload}.
     *
     * @see XMLKVTransaction#getGeneration
     */
    public synchronized int getGeneration() {
        return this.generation;
    }

    /**
     * Check the XML file's timestamp and reload it if it has been modified since the most recent
     * read or write by this instance.
     *
     * @return true if file was updated and re-read, otherwise false
     */
    public synchronized boolean checkForOutOfBandUpdate() {
        if (this.file == null)
            return false;
        final long fileTime = this.file.lastModified();
        if (fileTime == 0)
            return false;
        if (this.timestamp != 0) {
            if (fileTime <= this.timestamp)
                return false;
            this.log.info("detected out-of-band update of XMLKVDatabase file `" + this.file + "'; reloading");
        }
        this.readXML();
        return true;
    }

    @Override
    protected void checkState(SimpleKVTransaction tx) {
        this.checkForOutOfBandUpdate();
        final int txGeneration = ((XMLKVTransaction)tx).getGeneration();
        if (txGeneration != this.generation) {
            throw new RetryTransactionException(tx, "XML file changed since transaction started (generation number changed from "
              + txGeneration + " to " + this.generation + ")");
        }
    }

    @Override
    protected void postCommit(SimpleKVTransaction tx, boolean successful) {

        // If something weird happened, reload from storage
        if (!successful) {
            this.readXML();
            return;
        }

        // Persist data to file
        this.writeXML();
    }

    protected synchronized void readXML() {

        // Clear all existing keys
        this.kv.removeRange(null, null);

        // Snapshot file's current modification timestamp
        final long newTimestamp = this.file != null ? this.file.lastModified() : 0;

        // Open file input
        InputStream input;
        try {
            input = this.repository.getInputStream();
        } catch (FileNotFoundException e) {

            // If this is not the first load, file must have mysteriously disappeared
            if (this.generation != 0)
                throw new KVDatabaseException(this, "error reading XML content: file not longer available", e);

            // Get default initial content instead, if any
            try {
                input = this.getInitialContent();
            } catch (IOException e2) {
                throw new KVDatabaseException(this, "error opening initial XML content", e2);
            }
            if (input == null)
                throw new KVDatabaseException(this, "file `" + this.file + "' not found and no initial content is configured", e);
            this.log.info("file `" + this.file + "' not found; applying default initial content");
        } catch (IOException e) {
            throw new KVDatabaseException(this, "error opening XML content", e);
        }

        // Read XML
        try {
            this.serializer.read(new BufferedInputStream(input));
        } catch (XMLStreamException e) {
            throw new KVDatabaseException(this, "error reading XML content", e);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                // ignore
            }
        }

        // Update timestamp and generation number
        if (newTimestamp != 0)
            this.timestamp = newTimestamp;
        this.generation++;
    }

    protected synchronized void writeXML() {
        boolean successful = false;
        try {
            final OutputStream output = this.repository.getOutputStream();
            try {
                this.serializer.write(output, true);
                if (output instanceof AtomicUpdateFileOutputStream)
                    ((AtomicUpdateFileOutputStream)output).getFD().sync();
                output.close();
                if (this.file != null)
                    this.timestamp = this.file.lastModified();
                successful = true;
            } finally {
                if (!successful && output instanceof AtomicUpdateFileOutputStream)
                    ((AtomicUpdateFileOutputStream)output).cancel();
            }
        } catch (IOException e) {
            throw new KVDatabaseException(this, "error writing XML content", e);
        } catch (XMLStreamException e) {
            throw new KVDatabaseException(this, "error writing XML content", e);
        }
    }
}

