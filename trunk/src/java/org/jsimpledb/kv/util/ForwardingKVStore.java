
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv.util;

import java.util.Iterator;

import org.jsimpledb.kv.KVPair;
import org.jsimpledb.kv.KVStore;

/**
 * Forwards all {@link KVStore} operations to another underlying {@link KVStore}.
 */
public abstract class ForwardingKVStore implements KVStore {

    /**
     * Get the underlying {@link KVStore}.
     *
     * @return underlying {@link KVStore}
     */
    protected abstract KVStore delegate();

// KVStore

    @Override
    public byte[] get(byte[] key) {
        return this.delegate().get(key);
    }

    @Override
    public KVPair getAtLeast(byte[] minKey) {
        return this.delegate().getAtLeast(minKey);
    }

    @Override
    public KVPair getAtMost(byte[] maxKey) {
        return this.delegate().getAtMost(maxKey);
    }

    @Override
    public Iterator<KVPair> getRange(byte[] minKey, byte[] maxKey, boolean reverse) {
        return this.delegate().getRange(minKey, maxKey, reverse);
    }

    @Override
    public void put(byte[] key, byte[] value) {
        this.delegate().put(key, value);
    }

    @Override
    public void remove(byte[] key) {
        this.delegate().remove(key);
    }

    @Override
    public void removeRange(byte[] minKey, byte[] maxKey) {
        this.delegate().removeRange(minKey, maxKey);
    }

    @Override
    public void adjustCounter(byte[] key, long amount) {
        this.delegate().adjustCounter(key, amount);
    }

    @Override
    public byte[] encodeCounter(long value) {
        return this.delegate().encodeCounter(value);
    }

    @Override
    public long decodeCounter(byte[] bytes) {
        return this.delegate().decodeCounter(bytes);
    }
}

