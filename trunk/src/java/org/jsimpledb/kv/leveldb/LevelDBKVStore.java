
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv.leveldb;

import java.io.Closeable;
import java.util.NoSuchElementException;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteBatch;
import org.jsimpledb.kv.AbstractKVStore;
import org.jsimpledb.kv.KVPair;
import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.CloseableTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.jsimpledb.kv.KVStore} view of a LevelDB database.
 *
 * <p>
 * Instances must be {@link #close}'d when no longer needed to avoid leaking resources associated with iterators.
 * </p>
 */
public class LevelDBKVStore extends AbstractKVStore implements Closeable {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final CloseableTracker cursorTracker = new CloseableTracker();
    private final ReadOptions readOptions;
    private final WriteBatch writeBatch;
    private final DB db;

    private boolean closed;

    /**
     * Convenience constructor. Uses default read options and no write batching.
     *
     * @param db database
     */
    public LevelDBKVStore(DB db) {
        this(db, null, null);
    }

    /**
     * Constructor.
     *
     * @param db database
     * @param readOptions read options, or null for the default
     * @param writeBatch batch for write operations, or null for none
     * @throws IllegalArgumentException if {@code db} is null
     */
    public LevelDBKVStore(DB db, ReadOptions readOptions, WriteBatch writeBatch) {
        if (db == null)
            throw new IllegalArgumentException("null db");
        this.db = db;
        this.readOptions = readOptions != null ? readOptions : new ReadOptions();
        this.writeBatch = writeBatch;
        if (this.log.isTraceEnabled())
            this.log.trace("created " + this);
    }

// KVStore

    @Override
    public synchronized byte[] get(byte[] key) {
        key.getClass();
        if (this.closed)
            throw new IllegalStateException("the store is closed");
        this.cursorTracker.poll();
        return this.db.get(key, this.readOptions);
    }

    @Override
    public synchronized java.util.Iterator<KVPair> getRange(byte[] minKey, byte[] maxKey, boolean reverse) {
        if (this.closed)
            throw new IllegalStateException("the store is closed");
        this.cursorTracker.poll();
        return new Iterator(this.db.iterator(this.readOptions), minKey, maxKey, reverse);
    }

    @Override
    public synchronized void put(byte[] key, byte[] value) {
        key.getClass();
        value.getClass();
        if (this.closed)
            throw new IllegalStateException("the store is closed");
        this.cursorTracker.poll();
        if (this.writeBatch != null)
            this.writeBatch.put(key, value);
        else
            this.db.put(key, value);
    }

    @Override
    public synchronized void remove(byte[] key) {
        key.getClass();
        if (this.closed)
            throw new IllegalStateException("the store is closed");
        this.cursorTracker.poll();
        if (this.writeBatch != null)
            this.writeBatch.delete(key);
        else
            this.db.delete(key);
    }

// Object

    /**
     * Finalize this instance. Invokes {@link #close} to close any unclosed iterators.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            this.close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
          + "[db=" + this.db
          + ",options=" + this.readOptions
          + (this.writeBatch != null ? ",writeBatch=" + this.writeBatch : "")
          + "]";
    }

// Closeable

    /**
     * Close this instance.
     *
     * <p>
     * This closes any unclosed iterators returned from {@link #getRange getRange()}.
     * This does not close the underlying {@link DB} or any associated {@link WriteBatch}.
     */
    @Override
    public synchronized void close() {
        if (this.closed)
            return;
        this.closed = true;
        if (this.log.isTraceEnabled())
            this.log.trace("closing " + this);
        this.cursorTracker.close();
    }

// Iterator

    private final class Iterator implements java.util.Iterator<KVPair>, Closeable {

        private final DBIterator cursor;
        private final byte[] minKey;
        private final byte[] maxKey;
        private final boolean reverse;

        private KVPair next;
        private byte[] removeKey;
        private boolean finished;
        private boolean closed;

        Iterator(DBIterator cursor, byte[] minKey, byte[] maxKey, boolean reverse) {

            // Make sure we eventually close the iterator
            LevelDBKVStore.this.cursorTracker.add(this, cursor);

            // Sanity checks
            assert Thread.holdsLock(LevelDBKVStore.this);
            if (minKey != null && maxKey != null && ByteUtil.compare(minKey, maxKey) > 0)
                throw new IllegalArgumentException("minKey > maxKey");

            // Initialize
            this.cursor = cursor;
            this.minKey = minKey;
            this.maxKey = maxKey;
            this.reverse = reverse;
            if (reverse) {
                if (maxKey != null)
                    this.cursor.seek(maxKey);
                else
                    this.cursor.seekToLast();
            } else {
                if (minKey != null)
                    this.cursor.seek(minKey);
            }

            // Debug
            if (LevelDBKVStore.this.log.isTraceEnabled())
                LevelDBKVStore.this.log.trace("created " + this);
        }

    // Iterator

        @Override
        public synchronized boolean hasNext() {
            if (this.closed)
                throw new IllegalStateException();
            return this.next != null || this.findNext();
        }

        @Override
        public synchronized KVPair next() {
            if (this.closed)
                throw new IllegalStateException();
            if (this.next == null && !this.findNext())
                throw new NoSuchElementException();
            assert this.next != null;
            final KVPair pair = this.next;
            this.removeKey = pair.getKey();
            this.next = null;
            return pair;
        }

        @Override
        public synchronized void remove() {
            if (this.closed || this.removeKey == null)
                throw new IllegalStateException();
            LevelDBKVStore.this.remove(this.removeKey);
            this.removeKey = null;
        }

        private synchronized boolean findNext() {

            // Sanity check
            assert this.next == null;
            if (this.finished)
                return false;

            // Advance LevelDB cursor
            try {
                this.next = new KVPair(this.reverse ? this.cursor.prev() : this.cursor.next());
            } catch (NoSuchElementException e) {
                this.finished = true;
                return false;
            }

            // Check limit bound
            if (this.reverse ?
              (this.minKey != null && ByteUtil.compare(this.next.getKey(), this.minKey) < 0) :
              (this.maxKey != null && ByteUtil.compare(this.next.getKey(), this.maxKey) >= 0)) {
                this.next = null;
                this.finished = true;
                return false;
            }

            // Done
            return true;
        }

    // Closeable

        @Override
        public synchronized void close() {
            if (this.closed)
                return;
            this.closed = true;
            if (LevelDBKVStore.this.log.isTraceEnabled())
                LevelDBKVStore.this.log.trace("closing " + this);
            try {
                this.cursor.close();
            } catch (Throwable e) {
                LevelDBKVStore.this.log.debug("caught exception closing db iterator (ignoring)", e);
            }
        }

    // Object

        @Override
        public String toString() {
            return LevelDBKVStore.class.getSimpleName() + "." + this.getClass().getSimpleName()
              + "[minKey=" + ByteUtil.toString(this.minKey)
              + ",maxKey=" + ByteUtil.toString(this.maxKey)
              + (this.reverse ? ",reverse" : "")
              + "]";
        }
    }
}

