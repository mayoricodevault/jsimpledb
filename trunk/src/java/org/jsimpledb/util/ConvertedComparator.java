
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.util;

import com.google.common.base.Converter;

import java.util.Comparator;

/**
 * Comparator that compares using converted values.
 */
class ConvertedComparator<E, W> implements Comparator<E> {

    private final Comparator<? super W> comparator;
    private final Converter<E, W> converter;

    ConvertedComparator(Comparator<? super W> comparator, Converter<E, W> converter) {
        if (converter == null)
            throw new IllegalArgumentException("null converter");
        this.comparator = comparator;
        this.converter = converter;
    }

    public Converter<E, W> getConverter() {
        return this.converter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int compare(E obj1, E obj2) {
        final W wobj1 = this.converter.convert(obj1);
        final W wobj2 = this.converter.convert(obj2);
        return this.comparator != null ? this.comparator.compare(wobj1, wobj2) : ((Comparable<W>)wobj1).compareTo(wobj2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final ConvertedComparator<?, ?> that = (ConvertedComparator<?, ?>)obj;
        return (this.comparator != null ? this.comparator.equals(that.comparator) : that.comparator == null)
          && this.converter.equals(that.converter);
    }

    @Override
    public int hashCode() {
        return (this.comparator != null ? this.comparator.hashCode() : 0) ^ this.converter.hashCode();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[converter=" + this.converter + ",comparator=" + this.comparator + "]";
    }
}

