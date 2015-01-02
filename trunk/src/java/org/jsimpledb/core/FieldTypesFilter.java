
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

import com.google.common.primitives.Bytes;

import java.util.Arrays;
import java.util.List;

import org.jsimpledb.kv.KeyFilter;
import org.jsimpledb.kv.KeyFilterUtil;
import org.jsimpledb.kv.KeyRanges;
import org.jsimpledb.util.ByteReader;
import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.ByteWriter;

/**
 * A {@link KeyFilter} that accepts any key that is the concatenation of some prefix plus valid {@link FieldType} encoded values,
 * where each encoded value must pass a corresponding {@link KeyFilter} that is applied to just that portion of the key.
 *
 * <p>
 * Instances are immutable.
 * </p>
 */
class FieldTypesFilter implements KeyFilter {

    private static final int OK = 0;
    private static final int INVALID = 1;
    private static final int TOOSHORT = 2;

    private final byte[] prefix;
    private final FieldType<?>[] fieldTypes;
    private final KeyFilter[] filters;

// Public constructors

    /**
     * Construct an instance that has no per-field {@link KeyFilter}s applied yet.
     *
     * @param prefix mandatory prefix, or null for none
     * @param fieldTypes field types
     * @throws IllegalArgumentException if {@code fieldTypes} is null
     */
    public FieldTypesFilter(byte[] prefix, List<FieldType<?>> fieldTypes) {
        this(prefix, fieldTypes != null ? fieldTypes.toArray(new FieldType<?>[fieldTypes.size()]) : null);
    }

    /**
     * Construct an instance that has no per-field {@link KeyFilter}s applied yet.
     *
     * @param prefix mandatory prefix, or null for none
     * @param fieldTypes field types
     * @throws IllegalArgumentException if {@code fieldTypes} is null
     */
    public FieldTypesFilter(byte[] prefix, FieldType<?>... fieldTypes) {
        if (fieldTypes == null)
            throw new IllegalArgumentException("null fieldTypes");
        this.prefix = prefix != null ? prefix.clone() : ByteUtil.EMPTY;
        this.fieldTypes = fieldTypes;
        for (FieldType<?> fieldType : this.fieldTypes) {
            if (fieldType == null)
                throw new IllegalArgumentException("null fieldType");
        }
        this.filters = new KeyFilter[this.fieldTypes.length];
    }

// Package constructors

    FieldTypesFilter(byte[] prefix, FieldType<?>[] fieldTypes, KeyFilter[] filters, int start, int end) {
        this(prefix, Arrays.copyOfRange(fieldTypes, start, end));
        if (filters == null || filters.length != fieldTypes.length)
            throw new IllegalArgumentException("bogus filters");
        for (int i = 0; i < this.fieldTypes.length; i++)
            this.filters[i] = filters[start + i];
    }

// Private constructors

    /**
     * Copy constructor.
     */
    private FieldTypesFilter(FieldTypesFilter original) {
        this.prefix = original.prefix;
        this.fieldTypes = original.fieldTypes;
        this.filters = original.filters.clone();
    }

// Methods

    /**
     * Get the {@link FieldType}s associated with this instance.
     *
     * @return unmodifiable list of {@link FieldType}s
     */
    public List<FieldType<?>> getFieldTypes() {
        return Arrays.asList(this.fieldTypes.clone());
    }

    /**
     * Get the key filter for the {@link FieldType} at the specified index, if any.
     *
     * @return filter for the encoded field at index {@code index}, or null if no filter is applied
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public KeyFilter getFilter(int index) {
        return this.filters[index];
    }

    /**
     * Determine whether any {@link FieldType}s in this instance have a filter applied.
     */
    public boolean hasFilters() {
        for (KeyFilter filter : this.filters) {
            if (filter != null)
                return true;
        }
        return false;
    }

    /**
     * Create a new instance with the given {@link KeyFilter} applied to encoded field values at the specified index.
     * This method works cummulatively: if this instance already has a filter for the field, the new instance filters
     * to the intersection of the existing filter and the given filter.
     *
     * @param index field index (zero-based)
     * @param keyFilter key filtering to apply
     * @throws IllegalArgumentException if {@code keyFilter} is null
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public FieldTypesFilter filter(int index, KeyFilter keyFilter) {
        if (keyFilter == null)
            throw new IllegalArgumentException("null keyFilter");
        if (keyFilter instanceof KeyRanges && ((KeyRanges)keyFilter).isFull())
            return this;
        if (this.filters[index] != null)
            keyFilter = KeyFilterUtil.intersection(keyFilter, this.filters[index]);
        final FieldTypesFilter copy = new FieldTypesFilter(this);
        copy.filters[index] = keyFilter;
        return copy;
    }

    @Override
    public String toString() {
        return "FieldTypesFilter"
          + "[prefix=" + ByteUtil.toString(this.prefix)
          + ",fieldTypes=" + Arrays.asList(this.fieldTypes)
          + ",filters=" + Arrays.asList(this.filters)
          + "]";
    }

// KeyFilter

    @Override
    public boolean contains(byte[] key) {
        final byte[] next = this.seekHigher(key);
        return next != null && Arrays.equals(next, key);
    }

    @Override
    public byte[] seekHigher(byte[] key) {

        // Sanity check
        if (key == null)
            throw new IllegalArgumentException("null key");

        // Check prefix
        if (!ByteUtil.isPrefixOf(this.prefix, key)) {
            if (ByteUtil.compare(key, this.prefix) > 0)
                return null;
            return this.prefix;
        }

        // Check fields
        final ByteReader reader = new ByteReader(key, this.prefix.length);
        for (int i = 0; i < this.fieldTypes.length; i++) {

            // Attempt to decode next field value
            final int fieldStart = reader.getOffset();
            switch (this.decode(this.fieldTypes[i], reader)) {
            case INVALID:
                return ByteUtil.getNextKey(reader.getBytes(0, reader.getOffset()));
            case TOOSHORT:
                return ByteUtil.getKeyAfterPrefix(reader.getBytes(0, reader.getOffset()));
            default:
                break;
            }

            // Check filter, if any
            final KeyFilter filter = this.filters[i];
            if (filter == null)
                continue;
            final byte[] fieldValue = Arrays.copyOfRange(key, fieldStart, reader.getOffset());
            final byte[] next = filter.seekHigher(fieldValue);
            if (next == null)
                return ByteUtil.getKeyAfterPrefix(reader.getBytes(0, fieldStart));
            if (!Arrays.equals(next, fieldValue))
                return Bytes.concat(reader.getBytes(0, fieldStart), next);
        }

        // All fields decoded OK - return original key
        return key;
    }

    @Override
    public byte[] seekLower(byte[] key) {

        // Sanity check
        if (key == null)
            throw new IllegalArgumentException("null key");

        // Check prefix and handle max upper bound
        boolean fromTheTop = key.length == 0;
        if (!fromTheTop && !ByteUtil.isPrefixOf(this.prefix, key)) {
            if (ByteUtil.compare(key, this.prefix) < 0)
                return null;
            fromTheTop = true;
        }
        if (fromTheTop)
            return ByteUtil.getKeyAfterPrefix(this.prefix);

        // Check fields in order, building a concatenated upper bound. We can only proceed from one field to the next
        // when the first field is validly decoded, the corresponding filter exists, and filter.nextLower() returns
        // the same field value we decoded. Otherwise we have to stop because any smaller field value could possibly be valid.
        final ByteReader reader = new ByteReader(key, this.prefix.length);
        final ByteWriter writer = new ByteWriter(key.length);
        writer.write(key, 0, this.prefix.length);
        for (int i = 0; i < this.fieldTypes.length; i++) {
            final FieldType<?> fieldType = this.fieldTypes[i];
            final KeyFilter filter = this.filters[i];

            // Attempt to decode next field value
            final int fieldStart = reader.getOffset();
            final boolean decodeOK = this.decode(fieldType, reader) == OK;
            final int fieldStop = reader.getOffset();

            // Get (partially) decoded field value
            final byte[] fieldValue = Arrays.copyOfRange(key, fieldStart, fieldStop);

            // If there is no filter (or nothing to filter), stop if decode failed, otherwise proceed
            if (filter == null || fieldValue.length == 0) {
                writer.write(fieldValue);
                if (!decodeOK)
                    break;
                assert fieldValue.length > 0;
                continue;
            }

            // Apply filter to the (partially) decoded field value; if null returned, retreat to previous field
            final byte[] next = filter.seekLower(fieldValue);
            if (next == null)
                break;

            // If filter returned a strictly lower upper bound, or decode failed, we have to stop now
            if (!Arrays.equals(next, fieldValue) || !decodeOK) {
                writer.write(next);
                break;
            }

            // Filter returned same field value we gave it, so proceed to the next field
            writer.write(fieldValue);
        }

        // Done
        return writer.getBytes();
    }

// Internal methods

    private int decode(FieldType<?> fieldType, ByteReader reader) {
        try {
            fieldType.skip(reader);
        } catch (IllegalArgumentException e) {
            return INVALID;
        } catch (IndexOutOfBoundsException e) {
            return TOOSHORT;
        }
        return OK;
    }
}

