
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
import java.util.NavigableMap;
import java.util.NavigableSet;

import org.dellroad.stuff.java.Primitive;
import org.jsimpledb.change.FieldChange;
import org.jsimpledb.change.SimpleFieldChange;
import org.jsimpledb.core.Transaction;
import org.jsimpledb.schema.SimpleSchemaField;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Represents a simple field in a {@link JClass} or a simple sub-field of a complex field in a {@link JClass}.
 */
public class JSimpleField extends JField {

    final TypeToken<?> typeToken;
    final String typeName;
    final boolean indexed;
    final Method setter;

    JSimpleField(String name, int storageId, TypeToken<?> typeToken,
      String typeName, boolean indexed, String description, Method getter, Method setter) {
        super(name, storageId, description, getter);
        if (typeName == null)
            throw new IllegalArgumentException("null typeName");
        if (typeToken == null)
            throw new IllegalArgumentException("null typeToken");
        this.typeToken = typeToken;
        this.typeName = typeName;
        this.indexed = indexed;
        this.setter = setter;
    }

    /**
     * Get name of this field's {@link org.jsimpledb.core.FieldType}.
     */
    public String getTypeName() {
        return this.typeName;
    }

    /**
     * Get whether this field is indexed.
     */
    public boolean isIndexed() {
        return this.indexed;
    }

    /**
     * Get the setter method associated with this field.
     *
     * @return field property setter method, or null if this field is a sub-field of a complex field
     */
    public Method getSetter() {
        return this.setter;
    }

    @Override
    SimpleSchemaField toSchemaItem() {
        final SimpleSchemaField schemaField = new SimpleSchemaField();
        this.initialize(schemaField);
        return schemaField;
    }

    void initialize(SimpleSchemaField schemaField) {
        super.initialize(schemaField);
        schemaField.setType(this.typeName);
        schemaField.setIndexed(this.indexed);
    }

    @Override
    void outputMethods(final ClassGenerator<?> generator, ClassWriter cw) {

        // Getter
        this.outputReadMethod(generator, cw, ClassGenerator.READ_SIMPLE_FIELD_METHOD);

        // Setter
        final Method writeMethod = ClassGenerator.WRITE_SIMPLE_FIELD_METHOD;
        generator.overrideBeanMethod(cw, this.setter, this.storageId, new ClassGenerator.CodeEmitter() {
            @Override
            public void emit(MethodVisitor mv) {

                // Push field value
                mv.visitVarInsn(Type.getType(JSimpleField.this.typeToken.getRawType()).getOpcode(Opcodes.ILOAD), 1);

                // Wrap result if needed
                if (JSimpleField.this.typeToken.isPrimitive())
                    generator.wrap(mv, Primitive.get(JSimpleField.this.typeToken.getRawType()));

                // Invoke Transaction.writeSimpleField()
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(JTransaction.class),
                  writeMethod.getName(), Type.getMethodDescriptor(writeMethod));
            }
        });
    }

    @Override
    void registerChangeListener(Transaction tx, int[] path, AllChangesListener listener) {
        tx.addSimpleFieldChangeListener(this.storageId, path, listener);
    }

    @Override
    <T> void addChangeParameterTypes(List<TypeToken<?>> types, TypeToken<T> targetType) {
        this.addChangeParameterTypes(types, targetType, this.typeToken);
    }

    // This method exists solely to bind the generic type parameters
    @SuppressWarnings("serial")
    private <T, V> void addChangeParameterTypes(List<TypeToken<?>> types, TypeToken<T> targetType, TypeToken<V> fieldType) {
        types.add(new TypeToken<FieldChange<T>>() { }
          .where(new TypeParameter<T>() { }, targetType));
        types.add(new TypeToken<SimpleFieldChange<T, V>>() { }
          .where(new TypeParameter<T>() { }, targetType)
          .where(new TypeParameter<V>() { }, fieldType.wrap()));
    }

    /**
     * Add valid return types for @IndexQuery-annotated methods that query this indexed field.
     */
    <T> void addIndexReturnTypes(List<TypeToken<?>> types, TypeToken<T> targetType) {
        this.addIndexReturnTypes(types, targetType, this.typeToken);
    }

    // This method exists solely to bind the generic type parameters
    @SuppressWarnings("serial")
    private <T, V> void addIndexReturnTypes(List<TypeToken<?>> types, TypeToken<T> targetType, TypeToken<V> fieldType) {
        types.add(new TypeToken<NavigableMap<V, NavigableSet<T>>>() { }
          .where(new TypeParameter<V>() { }, fieldType.wrap())
          .where(new TypeParameter<T>() { }, targetType));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Converter<?, ?> getConverter(JSimpleDB jdb) {
        if (Enum.class.isAssignableFrom(this.typeToken.getRawType()))
            return this.createEnumConverter((TypeToken<Enum>)this.typeToken);
        return null;
    }

    // This method exists solely to bind the generic type parameters
    private <T extends Enum<T>> EnumConverter<T> createEnumConverter(TypeToken<T> etype) {
        return new EnumConverter<T>(etype);
    }
}
