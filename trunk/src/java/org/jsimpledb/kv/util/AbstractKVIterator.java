
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv.util;

import java.util.Iterator;

import org.jsimpledb.kv.KVPair;
import org.jsimpledb.kv.KVPairIterator;
import org.jsimpledb.kv.KVStore;
import org.jsimpledb.kv.KeyFilter;
import org.jsimpledb.kv.KeyRange;
import org.jsimpledb.util.ByteReader;
import org.jsimpledb.util.ByteUtil;
import org.slf4j.LoggerFactory;

/**
 * {@link Iterator} implementation whose values derive from key/value {@code byte[]} pairs in a {@link KVStore}.
 * Instances support either forward or reverse iteration.
 *
 * <p><b>Subclass Methods</b></p>
 *
 * <p>
 * Subclasses must implement {@linkplain #decodePair decodePair()} to convert key/value pairs into iteration elements.
 * </p>
 *
 * <p>
 * This class provides a read-only implementation; for a mutable implementation, subclasses should also implement
 * {@link #doRemove doRemove()}.
 * </p>
 *
 * <p><b>Prefix Mode</b></p>
 *
 * <p>
 * Instances support "prefix mode" where the {@code byte[]} keys may have arbitrary trailing garbage, which is ignored,
 * and so by definition no key can be a prefix of any other key. The length of the prefix is determined implicitly by the
 * number of key bytes consumed by {@link #decodePair decodePair()}.
 * When not in prefix mode, {@link #decodePair decodePair()} <b>must</b> consume the entire key to preserve correct semantics.
 * </p>
 *
 * <p><b>Key Restrictions</b></p>
 *
 * <p>
 * Instances are configured with an (optional) {@link KeyRange} that restricts the iteration to the specified range.
 * </p>
 *
 * <p>
 * Instances also support filtering visible values using a {@link KeyFilter}.
 * To be visible in the iteration, keys must both be within the {@link KeyRange} and pass the {@link KeyFilter}.
 * </p>
 *
 * <p><b>Concurrent Modification</b></p>
 *
 * <p>
 * Instances of this class are thread safe.
 * </p>
 *
 * <p>
 * Internally, when not in prefix mode and no {@link KeyFilter} is configured, this instance will rely on the
 * iteration from {@link KVStore#getRange KVStore.getRange()}; otherwise, it will use a {@link KVPairIterator}.
 * Therefore, in the former case, whether this iteration always reflects the current state of the underlying
 * {@link KVStore} depends on the behavior of {@link KVStore#getRange KVStore.getRange()}.
 * </p>
 *
 * @see AbstractKVNavigableMap
 * @see AbstractKVNavigableSet
 * @param <E> iteration element type
 */
public abstract class AbstractKVIterator<E> implements java.util.Iterator<E> {

    /**
     * The underlying {@link KVStore}.
     */
    protected final KVStore kv;

    /**
     * Whether we are in "prefix" mode.
     */
    protected final boolean prefixMode;

    /**
     * Whether this instance is iterating in the reverse direction.
     */
    protected final boolean reversed;

    // Iteration state
    private final Iterator<KVPair> pairIterator;
    private KVPair removePair;
    private E removeValue;

// Constructors

    /**
     * Convenience constructor for when there are no range restrictions.
     *
     * @param kv underlying {@link KVStore}
     * @param prefixMode whether to allow keys to have trailing garbage
     * @param reversed whether to iterate in the reverse direction
     */
    protected AbstractKVIterator(KVStore kv, boolean prefixMode, boolean reversed) {
        this(kv, prefixMode, reversed, null, null);
    }

    /**
     * Convenience constructor for when the range of visible {@link KVStore} keys is all keys sharing a given {@code byte[]} prefix.
     *
     * @param kv underlying {@link KVStore}
     * @param prefixMode whether to allow keys to have trailing garbage
     * @param reversed whether to iterate in the reverse direction
     * @param prefix prefix defining minimum and maximum keys
     * @throws IllegalArgumentException if {@code prefix} is null or empty
     */
    protected AbstractKVIterator(KVStore kv, boolean prefixMode, boolean reversed, byte[] prefix) {
        this(kv, prefixMode, reversed, KeyRange.forPrefix(prefix), null);
    }

    /**
     * Primary constructor.
     *
     * @param kv underlying {@link KVStore}
     * @param prefixMode whether to allow keys to have trailing garbage
     * @param reversed whether to iterate in the reverse direction
     * @param keyRange key range restriction, or null for none
     * @param keyFilter key filter, or null for none
     * @throws IllegalArgumentException if {@code kv} is null
     */
    protected AbstractKVIterator(KVStore kv, boolean prefixMode, boolean reversed, KeyRange keyRange, KeyFilter keyFilter) {
        if (kv == null)
            throw new IllegalArgumentException("null kv");
        this.kv = kv;
        this.prefixMode = prefixMode;
        this.reversed = reversed;

        // If possible use a straight KVStore iterator which is more efficient than a KVPairIterator
        if (!this.prefixMode && keyFilter == null) {
            final byte[] minKey = keyRange != null ? keyRange.getMin() : null;
            final byte[] maxKey = keyRange != null ? keyRange.getMax() : null;
            this.pairIterator = this.kv.getRange(minKey, maxKey, this.reversed);
        } else
            this.pairIterator = new KVPairIterator(this.kv, keyRange, keyFilter, this.reversed);
    }

// Iterator

    @Override
    public boolean hasNext() {
        return this.pairIterator.hasNext();
    }

    @Override
    public synchronized E next() {

        // Get next key/value pair
        final KVPair pair = this.pairIterator.next();

        // Decode key/value pair
        final ByteReader keyReader = new ByteReader(pair.getKey());
        final E value = this.decodePair(pair, keyReader);
        if (!this.prefixMode && keyReader.remain() > 0) {
            LoggerFactory.getLogger(this.getClass()).error(this.getClass().getName() + "@"
              + Integer.toHexString(System.identityHashCode(this)) + ": " + keyReader.remain() + " undecoded bytes remain in key "
              + ByteUtil.toString(pair.getKey()) + ", value " + ByteUtil.toString(pair.getValue()) + " -> " + value);
        }

        // In prefix mode, skip over any additional keys having the same prefix as what we just decoded
        if (this.prefixMode) {
            final KVPairIterator kvPairIterator = (KVPairIterator)this.pairIterator;
            final byte[] prefix = keyReader.getBytes(0, keyReader.getOffset());
            kvPairIterator.setNextTarget(this.reversed ? prefix : ByteUtil.getKeyAfterPrefix(prefix));
        }

        // Done
        this.removePair = pair;
        this.removeValue = value;
        return value;
    }

    @Override
    public void remove() {
        final E removeValueCopy;
        final KVPair removePairCopy;
        synchronized (this) {
            if ((removePairCopy = this.removePair) == null)
                throw new IllegalStateException();
            this.removePair = null;
            removeValueCopy = this.removeValue;
        }
        this.doRemove(removeValueCopy, removePairCopy);
    }

// Subclass methods

    /**
     * Decode an iteration element from a key/value pair.
     *
     * <p>
     * If not in prefix mode, all of {@code keyReader} must be consumed; otherwise, the consumed portion
     * is the prefix and any following keys with the same prefix are ignored.
     * </p>
     *
     * @param pair key/value pair
     * @param keyReader key input
     * @return decoded iteration element
     */
    protected abstract E decodePair(KVPair pair, ByteReader keyReader);

    /**
     * Remove the previously iterated value.
     *
     * <p>
     * The implementation in {@link AbstractKVIterator} always throws {@link UnsupportedOperationException}.
     * Subclasses should override to make the iterator mutable.
     * </p>
     *
     * @param value most recent value returned by {@link #next}
     * @param pair the key/value pair corresponding to {@code value}
     */
    protected void doRemove(E value, KVPair pair) {
        throw new UnsupportedOperationException();
    }
}

