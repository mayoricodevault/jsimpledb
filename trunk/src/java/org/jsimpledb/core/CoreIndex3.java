
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.core;

import java.util.NavigableSet;

import org.jsimpledb.index.Index;
import org.jsimpledb.index.Index2;
import org.jsimpledb.index.Index3;
import org.jsimpledb.kv.KeyFilter;
import org.jsimpledb.tuple.Tuple2;
import org.jsimpledb.tuple.Tuple3;
import org.jsimpledb.tuple.Tuple4;

/**
 * Core API {@link Index} implementation representing a composite index on three fields.
 *
 * <p>
 * Instances are immutable.
 * </p>
 *
 * @param <V1> first index value type
 * @param <V2> second index value type
 * @param <V3> third index value type
 * @param <T> index target type
 */
public class CoreIndex3<V1, V2, V3, T> extends AbstractCoreIndex implements Index3<V1, V2, V3, T> {

// Constructors

    CoreIndex3(Transaction tx, Index3View<V1, V2, V3, T> indexView) {
        super(tx, 4, indexView);
    }

// Methods

    @Override
    public CoreIndex3<V1, V2, V3, T> filter(int index, KeyFilter filter) {
        return new CoreIndex3<V1, V2, V3, T>(this.tx, this.getIndex3View().filter(index, filter));
    }

    @SuppressWarnings("unchecked")
    Index3View<V1, V2, V3, T> getIndex3View() {
        return (Index3View<V1, V2, V3, T>)this.indexView;
    }

// Index3

    @Override
    public IndexSet<Tuple4<V1, V2, V3, T>> asSet() {

        // Get index view
        final Index3View<V1, V2, V3, T> iv = this.getIndex3View();

        // Create field type for Tuple4<V1, V2, V3, T>
        final Tuple4FieldType<V1, V2, V3, T> fieldType = new Tuple4FieldType<V1, V2, V3, T>(
          iv.getValue1Type(), iv.getValue2Type(), iv.getValue3Type(), iv.getTargetType());

        // Build set and apply filtering
        IndexSet<Tuple4<V1, V2, V3, T>> indexSet = new IndexSet<Tuple4<V1, V2, V3, T>>(this.tx,
          fieldType, iv.prefixMode, iv.prefix);
        if (iv.hasFilters())
            indexSet = indexSet.filterKeys(new IndexKeyFilter(this.tx, iv, 4));

        // Done
        return indexSet;
    }

    @Override
    public IndexMap<Tuple3<V1, V2, V3>, NavigableSet<T>> asMap() {

        // Get index view
        final Index3View<V1, V2, V3, T> iv = this.getIndex3View();

        // Create new IndexView
        final IndexView<Tuple3<V1, V2, V3>, T> tupleIV = iv.asTuple3IndexView();

        // Build map and apply filtering
        IndexMap<Tuple3<V1, V2, V3>, NavigableSet<T>> indexMap = new IndexMap.OfValues<Tuple3<V1, V2, V3>, T>(this.tx, tupleIV);
        if (tupleIV.hasFilters())
            indexMap = indexMap.filterKeys(new IndexKeyFilter(this.tx, tupleIV, 1));

        // Done
        return indexMap;
    }

    @Override
    public IndexMap<Tuple2<V1, V2>, Index<V3, T>> asMapOfIndex() {

        // Get index view
        final Index3View<V1, V2, V3, T> iv = this.getIndex3View();

        // Create new IndexView
        final Index2View<Tuple2<V1, V2>, V3, T> tupleIV = iv.asTuple2Index2View();

        // Build map and apply filtering
        IndexMap<Tuple2<V1, V2>, Index<V3, T>> indexMap = new IndexMap.OfIndex<Tuple2<V1, V2>, V3, T>(this.tx, tupleIV);
        if (iv.hasFilters())
            indexMap = indexMap.filterKeys(new IndexKeyFilter(this.tx, tupleIV, 1));

        // Done
        return indexMap;
    }

    @Override
    public IndexMap<V1, Index2<V2, V3, T>> asMapOfIndex2() {

        // Get index view
        final Index3View<V1, V2, V3, T> iv = this.getIndex3View();

        // Build map and apply filtering
        IndexMap<V1, Index2<V2, V3, T>> indexMap = new IndexMap.OfIndex2<V1, V2, V3, T>(this.tx, iv);
        if (iv.hasFilters())
            indexMap = indexMap.filterKeys(new IndexKeyFilter(this.tx, iv, 1));

        // Done
        return indexMap;
    }

    @Override
    public CoreIndex2<V1, V2, V3> asIndex2() {
        return new CoreIndex2<V1, V2, V3>(this.tx, this.getIndex3View().asIndex2View());
    }

    @Override
    public CoreIndex<V1, V2> asIndex() {
        return new CoreIndex<V1, V2>(this.tx, this.getIndex3View().asIndex2View().asIndexView());
    }
}

