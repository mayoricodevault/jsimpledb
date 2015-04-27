
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv;

import com.google.common.primitives.Bytes;

import java.util.Arrays;
import java.util.Comparator;

import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.SizeEstimating;
import org.jsimpledb.util.SizeEstimator;

/**
 * Represents a contiguous range of {@code byte[]} keys, when keys are sorted in unsigned lexical order.
 * Instances are defined by an inclusive lower bound and an exclusive upper bound.
 * The upper bound may be specified as null to represent no maximum.
 *
 * <p>
 * Instances are immutable: the minimum and maximum {@code byte[]} arrays are copied during
 * construction and when accessed by {@link #getMin} and {@link #getMax}.
 * </p>
 */
public class KeyRange implements SizeEstimating {

    /**
     * The {@link KeyRange} containing the full range (i.e., all keys).
     */
    public static final KeyRange FULL = new KeyRange(ByteUtil.EMPTY, null);

    /**
     * Sorts instances by {@linkplain KeyRange#getMin min value}, then {@linkplain KeyRange#getMax max value}.
     */
    public static final Comparator<KeyRange> SORT_BY_MIN = new Comparator<KeyRange>() {
        @Override
        public int compare(KeyRange keyRange1, KeyRange keyRange2) {
            int diff = ByteUtil.compare(keyRange1.min, keyRange2.min);
            if (diff != 0)
                return diff;
            diff = KeyRange.compare(keyRange1.max, keyRange2.max);
            if (diff != 0)
                return diff;
            return 0;
        }
    };

    /**
     * Sorts instances by {@linkplain KeyRange#getMax max value}, then {@linkplain KeyRange#getMin min value}.
     */
    public static final Comparator<KeyRange> SORT_BY_MAX = new Comparator<KeyRange>() {
        @Override
        public int compare(KeyRange keyRange1, KeyRange keyRange2) {
            int diff = KeyRange.compare(keyRange1.max, keyRange2.max);
            if (diff != 0)
                return diff;
            diff = ByteUtil.compare(keyRange1.min, keyRange2.min);
            if (diff != 0)
                return diff;
            return 0;
        }
    };

    /**
     * Lower bound (inclusive), or null for no minimum. Subclasses must <b>not</b> modify the array (to preserve immutability).
     */
    protected final byte[] min;

    /**
     * Upper bound (exclusive), or null for no maximum. Subclasses must <b>not</b> modify the array (to preserve immutability).
     */
    protected final byte[] max;

// Constructors

    /**
     * Constructor.
     *
     * @param min minimum key (inclusive); must not be null
     * @param max maximum key (exclusive), or null for no maximum
     * @throws IllegalArgumentException if {@code min} is null
     * @throws IllegalArgumentException if {@code min > max}
     */
    public KeyRange(byte[] min, byte[] max) {
        if (min == null)
            throw new IllegalArgumentException("null min");
        if (KeyRange.compare(min, max) > 0)
            throw new IllegalArgumentException("min = " + ByteUtil.toString(min) + " > max = " + ByteUtil.toString(max));
        this.min = min.clone();
        this.max = max != null ? max.clone() : null;
    }

    /**
     * Construct key range containing a single key.
     *
     * @param key the key contained in the range
     * @throws IllegalArgumentException if {@code key} is null
     */
    public KeyRange(byte[] key) {
        if (key == null)
            throw new IllegalArgumentException("null key");
        this.min = key.clone();
        this.max = ByteUtil.getNextKey(this.min);
    }

    /**
     * Construct an instance containing all keys with the given prefix.
     *
     * @param prefix prefix of all keys in the range
     * @return range of keys prefixed by {@code prefix}
     * @throws IllegalArgumentException if {@code prefix} is null
     */
    public static KeyRange forPrefix(byte[] prefix) {
        if (prefix == null)
            throw new IllegalArgumentException("null prefix");
        if (prefix.length == 0)
            return KeyRange.FULL;
        /*final*/ byte[] maxKey;
        try {
            maxKey = ByteUtil.getKeyAfterPrefix(prefix);
        } catch (IllegalArgumentException e) {
            maxKey = null;
        }
        return new KeyRange(prefix, maxKey);
    }

// Instance Methods

    /**
     * Get range minimum (inclusive).
     *
     * @return inclusivie minimum, never null
     */
    public byte[] getMin() {
        return this.min.clone();
    }

    /**
     * Get range maximum (exclusive), or null if there is no upper bound.
     *
     * @return exclusivie maximum, or null for none
     */
    public byte[] getMax() {
        return this.max == null ? null : this.max.clone();
    }

    /**
     * Determine if this key range overlaps the specified key range.
     *
     * @param range other instance
     * @return true if this instance overlaps {@code range}
     * @throws IllegalArgumentException if {@code range} is null
     */
    public boolean overlaps(KeyRange range) {
        if (range == null)
            throw new IllegalArgumentException("null range");
        return KeyRange.compare(this.min, range.max) < 0 && KeyRange.compare(range.min, this.max) < 0;
    }

    /**
     * Determine if this key range fully contains the specified key range.
     *
     * @param range other instance
     * @return true if this instance contains {@code range}
     * @throws IllegalArgumentException if {@code range} is null
     */
    public boolean contains(KeyRange range) {
        if (range == null)
            throw new IllegalArgumentException("null range");
        return KeyRange.compare(this.min, range.min) <= 0 && KeyRange.compare(this.max, range.max) >= 0;
    }

    /**
     * Determine if this key range contains the specified key.
     *
     * @param key key to test
     * @return true if this range contains {@code key}
     * @throws IllegalArgumentException if {@code key} is null
     */
    public boolean contains(byte[] key) {
        return this.compareTo(key) == 0;
    }

    /**
     * Determine whether this instance contains the full range covering all keys.
     *
     * @return true if this instance contains all keys
     */
    public boolean isFull() {
        return this.min.length == 0 && this.max == null;
    }

    /**
     * Determine whether this instance contains exactly one key.
     *
     * <p>
     * If so, {@link #getMin} returns the key.
     * </p>
     *
     * @return true if this instance contains exactly one key, otherwise false
     */
    public boolean isSingleKey() {
        return this.max != null && this.max.length == this.min.length + 1 && this.max[this.min.length] == 0;
    }

    /**
     * Determine whether this instance contains zero keys (implying {@link #getMin}{@code == }{@link #getMax}).
     *
     * @return true if this instance contains no keys
     */
    public boolean isEmpty() {
        return this.max != null && ByteUtil.compare(this.min, this.max) == 0;
    }

    /**
     * Create a new instance whose minimum and maximum keys are the same as this instance's
     * but with the given byte sequence prepended.
     *
     * @param prefix key range prefix
     * @return this range prefixed by {@code prefix}
     * @throws IllegalArgumentException if {@code prefix} is null
     */
    public KeyRange prefixedBy(byte[] prefix) {
        if (prefix == null)
            throw new IllegalArgumentException("null prefix");
        final byte[] prefixedMin = Bytes.concat(prefix, this.min);
        /*final*/ byte[] prefixedMax;
        if (this.max != null)
            prefixedMax = Bytes.concat(prefix, this.max);
        else {
            try {
                prefixedMax = ByteUtil.getKeyAfterPrefix(prefix);
            } catch (IllegalArgumentException e) {
                prefixedMax = null;
            }
        }
        return new KeyRange(prefixedMin, prefixedMax);
    }

    /**
     * Determine if this range is left of, contains, or is right of the given key.
     *
     * @param key key for comparison
     * @return -1 if this range is left of {@code key},
     *  0 if this range contains {@code key}, or 1 if this range is right of {@code key},
     * @throws IllegalArgumentException if {@code key} is null
     */
    public int compareTo(byte[] key) {
        if (key == null)
            throw new IllegalArgumentException("null key");
        if (KeyRange.compare(this.min, key) > 0)
            return 1;
        if (KeyRange.compare(this.max, key) <= 0)
            return -1;
        return 0;
    }

    /**
     * Compare two {@code byte[]} keys using unsigned lexical ordering, while also accepting
     * null values that represent "positive infinity".
     *
     * @param key1 first key
     * @param key2 second key
     * @return -1 if {@code key1 < key2}, 1 if {@code key1 > key2}, or zero if {@code key1 = key2}
     */
    public static int compare(byte[] key1, byte[] key2) {
        if (key1 == null && key2 == null)
            return 0;
        if (key1 == null)
            return 1;
        if (key2 == null)
            return -1;
        return ByteUtil.compare(key1, key2);
    }

// SizeEstimating

    @Override
    public void addTo(SizeEstimator estimator) {
        estimator
          .addObjectOverhead()
          .addField(this.min)
          .addField(this.max);
    }

// Object

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final KeyRange that = (KeyRange)obj;
        return Arrays.equals(this.min, that.min) && (this.max == null ? that.max == null : Arrays.equals(this.max, that.max));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.min) ^ (this.max != null ? Arrays.hashCode(this.max) : 0);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + ByteUtil.toString(this.min) + "," + ByteUtil.toString(this.max) + "]";
    }
}

