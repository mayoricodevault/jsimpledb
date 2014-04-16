
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import com.google.common.base.Converter;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.Method;
import java.util.List;

import org.jsimpledb.change.FieldChange;
import org.jsimpledb.change.ListFieldReplace;
import org.jsimpledb.change.SetFieldAdd;
import org.jsimpledb.change.SetFieldChange;
import org.jsimpledb.change.SetFieldClear;
import org.jsimpledb.change.SetFieldRemove;
import org.jsimpledb.core.Transaction;
import org.jsimpledb.schema.SetSchemaField;
import org.objectweb.asm.ClassWriter;

/**
 * Represents a set field in a {@link JClass}.
 */
public class JSetField extends JCollectionField {

    JSetField(String name, int storageId, JSimpleField elementField, String description, Method getter) {
        super(name, storageId, elementField, description, getter);
    }

    @Override
    SetSchemaField toSchemaItem() {
        final SetSchemaField schemaField = new SetSchemaField();
        super.initialize(schemaField);
        return schemaField;
    }

    @Override
    void outputMethods(ClassGenerator<?> generator, ClassWriter cw) {
        this.outputReadMethod(generator, cw, ClassGenerator.READ_SET_FIELD_METHOD);
    }

    @Override
    void registerChangeListener(Transaction tx, int[] path, AllChangesListener listener) {
        tx.addSetFieldChangeListener(this.storageId, path, listener);
    }

    @Override
    <T> void addChangeParameterTypes(List<TypeToken<?>> types, TypeToken<T> targetType) {
        this.addChangeParameterTypes(types, targetType, this.elementField.typeToken);
    }

    // This method exists solely to bind the generic type parameters
    @SuppressWarnings("serial")
    private <T, E> void addChangeParameterTypes(List<TypeToken<?>> types, TypeToken<T> targetType, TypeToken<E> elementType) {
        types.add(new TypeToken<FieldChange<T>>() { }
          .where(new TypeParameter<T>() { }, targetType));
        types.add(new TypeToken<SetFieldChange<T>>() { }
          .where(new TypeParameter<T>() { }, targetType));
        types.add(new TypeToken<SetFieldAdd<T, E>>() { }
          .where(new TypeParameter<T>() { }, targetType)
          .where(new TypeParameter<E>() { }, elementType.wrap()));
        types.add(new TypeToken<SetFieldClear<T, E>>() { }
          .where(new TypeParameter<T>() { }, targetType)
          .where(new TypeParameter<E>() { }, elementType.wrap()));
        types.add(new TypeToken<SetFieldRemove<T, E>>() { }
          .where(new TypeParameter<T>() { }, targetType)
          .where(new TypeParameter<E>() { }, elementType.wrap()));
        types.add(new TypeToken<ListFieldReplace<T, E>>() { }
          .where(new TypeParameter<T>() { }, targetType)
          .where(new TypeParameter<E>() { }, elementType.wrap()));
    }

    @Override
    <T> void addIndexEntryReturnTypes(List<TypeToken<?>> types, TypeToken<T> targetType, JSimpleField subField) {
        // there are no index entry types for sets
    }

    @Override
    int getIndexEntryQueryType(TypeToken<?> queryObjectType) {
        return 0;
    }

    @Override
    Method getIndexEntryQueryMethod(int queryType) {
        throw new UnsupportedOperationException();
    }

    @Override
    NavigableSetConverter<?, ?> getConverter(JSimpleDB jdb) {
        final Converter<?, ?> elementConverter = this.elementField.getConverter(jdb);
        if (elementConverter == null)
            return null;
        return this.createConverter(elementConverter);
    }

    // This method exists solely to bind the generic type parameters
    private <X, Y> NavigableSetConverter<X, Y> createConverter(Converter<X, Y> elementConverter) {
        return new NavigableSetConverter<X, Y>(elementConverter);
    }
}
