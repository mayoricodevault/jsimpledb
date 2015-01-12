
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import com.google.common.base.Converter;

import java.util.NavigableMap;
import java.util.NavigableSet;

import org.jsimpledb.index.Index;
import org.jsimpledb.index.Index2;
import org.jsimpledb.index.Index3;
import org.jsimpledb.tuple.Tuple2;
import org.jsimpledb.tuple.Tuple3;
import org.jsimpledb.tuple.Tuple4;
import org.jsimpledb.util.ConvertedNavigableMap;
import org.jsimpledb.util.ConvertedNavigableSet;

/**
 * Converter for {@link Index3}s.
 *
 * @param <V1> first value type of this index
 * @param <V2> second value type of this index
 * @param <V3> third value type of this index
 * @param <T> target type of this index
 * @param <WV1> first value type of wrapped index
 * @param <WV2> second value type of wrapped index
 * @param <WV3> third value type of wrapped index
 * @param <WT> target type of wrapped index
 */
class ConvertedIndex3<V1, V2, V3, T, WV1, WV2, WV3, WT> implements Index3<V1, V2, V3, T> {

    private final Index3<WV1, WV2, WV3, WT> index;
    private final Converter<V1, WV1> value1Converter;
    private final Converter<V2, WV2> value2Converter;
    private final Converter<V3, WV3> value3Converter;
    private final Converter<T, WT> targetConverter;

    public ConvertedIndex3(Index3<WV1, WV2, WV3, WT> index, Converter<V1, WV1> value1Converter,
      Converter<V2, WV2> value2Converter, Converter<V3, WV3> value3Converter, Converter<T, WT> targetConverter) {
        if (index == null)
            throw new IllegalArgumentException("null index");
        if (value1Converter == null)
            throw new IllegalArgumentException("null value1Converter");
        if (value2Converter == null)
            throw new IllegalArgumentException("null value2Converter");
        if (value3Converter == null)
            throw new IllegalArgumentException("null value3Converter");
        if (targetConverter == null)
            throw new IllegalArgumentException("null targetConverter");
        this.index = index;
        this.value1Converter = value1Converter;
        this.value2Converter = value2Converter;
        this.value3Converter = value3Converter;
        this.targetConverter = targetConverter;
    }

    @Override
    public NavigableSet<Tuple4<V1, V2, V3, T>> asSet() {
        return new ConvertedNavigableSet<Tuple4<V1, V2, V3, T>, Tuple4<WV1, WV2, WV3, WT>>(
          this.index.asSet(),
          new Tuple4Converter<V1, V2, V3, T, WV1, WV2, WV3, WT>(this.value1Converter,
            this.value2Converter, this.value3Converter, this.targetConverter));
    }

    @Override
    public NavigableMap<Tuple3<V1, V2, V3>, NavigableSet<T>> asMap() {
        return new ConvertedNavigableMap<Tuple3<V1, V2, V3>, NavigableSet<T>, Tuple3<WV1, WV2, WV3>, NavigableSet<WT>>(
          this.index.asMap(),
          new Tuple3Converter<V1, V2, V3, WV1, WV2, WV3>(this.value1Converter, this.value2Converter, this.value3Converter),
          new NavigableSetConverter<T, WT>(this.targetConverter));
    }

    @Override
    public NavigableMap<Tuple2<V1, V2>, Index<V3, T>> asMapOfIndex() {
        return new ConvertedNavigableMap<Tuple2<V1, V2>, Index<V3, T>, Tuple2<WV1, WV2>, Index<WV3, WT>>(
          this.index.asMapOfIndex(),
          new Tuple2Converter<V1, V2, WV1, WV2>(this.value1Converter, this.value2Converter),
          new IndexConverter<V3, T, WV3, WT>(this.value3Converter, this.targetConverter));
    }

    @Override
    public NavigableMap<V1, Index2<V2, V3, T>> asMapOfIndex2() {
        return new ConvertedNavigableMap<V1, Index2<V2, V3, T>, WV1, Index2<WV2, WV3, WT>>(
          this.index.asMapOfIndex2(),
          this.value1Converter,
          new Index2Converter<V2, V3, T, WV2, WV3, WT>(this.value2Converter, this.value3Converter, this.targetConverter));
    }

    @Override
    public Index2<V1, V2, V3> asIndex2() {
        return new ConvertedIndex2<V1, V2, V3, WV1, WV2, WV3>(this.index.asIndex2(),
          this.value1Converter, this.value2Converter, this.value3Converter);
    }

    @Override
    public Index<V1, V2> asIndex() {
        return new ConvertedIndex<V1, V2, WV1, WV2>(this.index.asIndex(), this.value1Converter, this.value2Converter);
    }
}

