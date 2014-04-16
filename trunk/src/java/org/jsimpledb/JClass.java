
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsimpledb.annotation.JSimpleClass;
import org.jsimpledb.core.DeleteAction;
import org.jsimpledb.core.EnumValue;
import org.jsimpledb.core.FieldType;
import org.jsimpledb.core.ObjId;
import org.jsimpledb.schema.SchemaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Information about a Java class that is used to represent a specific JSimpleDB object type.
 *
 * @param <T> the Java class
 */
public class JClass<T> extends JSchemaObject {

    final Logger log = LoggerFactory.getLogger(this.getClass());

    final JSimpleDB jdb;
    final TypeToken<T> typeToken;
    final TreeMap<Integer, JField> jfields = new TreeMap<>();
    final TreeMap<String, JField> jfieldsByName = new TreeMap<>();

    Set<OnCreateScanner<T>.MethodInfo> onCreateMethods;
    Set<OnDeleteScanner<T>.MethodInfo> onDeleteMethods;
    Set<OnChangeScanner<T>.MethodInfo> onChangeMethods;
    Set<ValidateScanner<T>.MethodInfo> validateMethods;
    ArrayList<OnVersionChangeScanner<T>.MethodInfo> onVersionChangeMethods;
    Set<IndexQueryScanner<T>.MethodInfo> indexQueryMethods;

    int[] subtypeStorageIds;
    Class<? extends T> subclass;
    Constructor<? extends T> constructor;

    /**
     * Constructor.
     *
     * @param jdb the associated {@link JSimpleDB}
     * @param name the name of the object type
     * @param storageId object type storage ID
     * @param type object type Java model class
     * @throws IllegalArgumentException if any parameter is null
     * @throws IllegalArgumentException if {@code storageId} is non-positive
     */
    JClass(JSimpleDB jdb, String name, int storageId, TypeToken<T> typeToken) {
        super(name, storageId, "object type `" + name + "' (" + typeToken + ")");
        if (jdb == null)
            throw new IllegalArgumentException("null jdb");
        if (name == null)
            throw new IllegalArgumentException("null name");
        this.jdb = jdb;
        this.typeToken = typeToken;
    }

    // Get generated subclass' constructor
    Constructor<? extends T> getConstructor() {
        if (this.constructor == null) {
            try {
                this.constructor = this.getSubclass().getConstructor(ObjId.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("internal error", e);
            }
            this.constructor.setAccessible(true);
        }
        return this.constructor;
    }

    // Get generated subclass
    Class<? extends T> getSubclass() {
        if (this.subclass == null)
            this.subclass = new ClassGenerator<T>(this).generateClass();
        return this.subclass;
    }

// Public API

    /**
     * Get the {@link JSimpleDB} with which this instance is associated.
     */
    public JSimpleDB getJSimpleDB() {
        return this.jdb;
    }

    /**
     * Get the Java model object type associated with this instance.
     */
    public TypeToken<T> getTypeToken() {
        return this.typeToken;
    }

    /**
     * Get all {@link JField}'s associated with this instance, indexed by storage ID.
     *
     * @return read-only mapping from storage ID to {@link JClass}
     */
    public SortedMap<Integer, JField> getJFieldsByStorageId() {
        return Collections.unmodifiableSortedMap(this.jfields);
    }

    /**
     * Get all {@link JField}'s associated with this instance, indexed by name.
     *
     * @return read-only mapping from storage ID to {@link JClass}
     */
    public SortedMap<String, JField> getJFieldsByName() {
        return Collections.unmodifiableSortedMap(this.jfieldsByName);
    }

// Internal methods

    void createFields() {

        // Scan for Simple fields
        final JFieldScanner<T> simpleFieldScanner = new JFieldScanner<T>(this);
        for (JFieldScanner<T>.MethodInfo info : simpleFieldScanner.findAnnotatedMethods()) {

            // Get info
            final org.jsimpledb.annotation.JField annotation = info.getAnnotation();
            final Method getter = info.getMethod();
            final String description = simpleFieldScanner.getAnnotationDescription() + " annotation on method " + getter;
            final String fieldName = this.getFieldName(annotation.name(), info, description);
            final TypeToken<?> fieldTypeToken = TypeToken.of(getter.getGenericReturnType());
            this.log.debug("found " + description);

            // Handle Counter fields
            if (fieldTypeToken.equals(TypeToken.of(Counter.class))) {

                // Sanity check annotation
                if (annotation.type().length() != 0)
                    throw new IllegalArgumentException("invalid " + description + ": counter fields must not specify a type");
                if (annotation.indexed())
                    throw new IllegalArgumentException("invalid " + description + ": counter fields cannot be indexed");

                // Create counter field
                final JCounterField jfield = new JCounterField(fieldName, annotation.storageId(),
                  "counter field `" + fieldName + "' of object type `" + this.name + "'", getter);
                jfield.parent = this;

                // Add field
                this.addField(jfield);
                this.log.debug("added counter field `" + fieldName + "' to object type `" + this.name + "'");
                continue;
            }

            // Find corresponding setter method
            final Matcher matcher = Pattern.compile("(is|get)(.+)").matcher(getter.getName());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid " + description
                  + ": can't infer setter method name because getter method name does not follow Java bean naming conventions");
            }
            final String setterName = "set" + matcher.group(2);
            Method setter = null;
            for (TypeToken<?> superType : this.typeToken.getTypes()) {
                try {
                    setter = superType.getRawType().getMethod(setterName, fieldTypeToken.getRawType());
                } catch (NoSuchMethodException e) {
                    continue;
                }
                break;
            }
            if (setter == null) {
                throw new IllegalArgumentException("invalid " + description
                  + ": can't find any corresponding setter method " + setterName + "() taking " + fieldTypeToken);
            }

            // Create simple field
            final JSimpleField jfield = this.createSimpleField(description, fieldTypeToken,
              fieldName, annotation.type(), annotation.storageId(), annotation.indexed(), annotation.onDelete(),
              getter, setter, "field `" + fieldName + "' of object type `" + this.name + "'");
            jfield.parent = this;

            // Add field
            this.addField(jfield);
            this.log.debug("added simple field `" + fieldName + "' to object type `" + this.name + "'");
        }

        // Scan for Set fields
        final JSetFieldScanner<T> setFieldScanner = new JSetFieldScanner<T>(this);
        for (JSetFieldScanner<T>.MethodInfo info : setFieldScanner.findAnnotatedMethods()) {

            // Get info
            final org.jsimpledb.annotation.JSetField annotation = info.getAnnotation();
            final org.jsimpledb.annotation.JField elementAnnotation = annotation.element();
            final Method getter = info.getMethod();
            final String description = setFieldScanner.getAnnotationDescription() + " annotation on method " + getter;
            final String fieldName = this.getFieldName(annotation.name(), info, description);
            this.log.debug("found " + description);

            // Get element type (the raw return type has already been validated by the annotation scanner)
            final TypeToken<?> elementType = this.getParameterType(description, getter, 0);

            // Create element sub-field
            final JSimpleField elementField = this.createSimpleField("element() property of " + description,
              elementType, null, elementAnnotation.type(), elementAnnotation.storageId(), elementAnnotation.indexed(),
              elementAnnotation.onDelete(), null, null,
              "element field of set field `" + fieldName + "' in object type `" + this.name + "'");

            // Create set field
            final JSetField jfield = new JSetField(fieldName, annotation.storageId(),
              elementField, "set field `" + fieldName + "' in object type `" + this.name + "'", getter);
            elementField.parent = jfield;

            // Add field
            this.addField(jfield);
            this.log.debug("added set field `" + fieldName + "' to object type `" + this.name + "'");
        }

        // Scan for List fields
        final JListFieldScanner<T> listFieldScanner = new JListFieldScanner<T>(this);
        for (JListFieldScanner<T>.MethodInfo info : listFieldScanner.findAnnotatedMethods()) {

            // Get info
            final org.jsimpledb.annotation.JListField annotation = info.getAnnotation();
            final org.jsimpledb.annotation.JField elementAnnotation = annotation.element();
            final Method getter = info.getMethod();
            final String description = listFieldScanner.getAnnotationDescription() + " annotation on method " + getter;
            final String fieldName = this.getFieldName(annotation.name(), info, description);
            this.log.debug("found " + description);

            // Get element type (the raw return type has already been validated by the annotation scanner)
            final TypeToken<?> elementType = this.getParameterType(description, getter, 0);

            // Create element sub-field
            final JSimpleField elementField = this.createSimpleField("element() property of " + description,
              elementType, null, elementAnnotation.type(), elementAnnotation.storageId(), elementAnnotation.indexed(),
              elementAnnotation.onDelete(), null, null,
              "element field of list field `" + fieldName + "' in object type `" + this.name + "'");

            // Create list field
            final JListField jfield = new JListField(fieldName, annotation.storageId(),
              elementField, "list field `" + fieldName + "' in object type `" + this.name + "'", getter);
            elementField.parent = jfield;

            // Add field
            this.addField(jfield);
            this.log.debug("added list field `" + fieldName + "' to object type `" + this.name + "'");
        }

        // Scan for Map fields
        final JMapFieldScanner<T> mapFieldScanner = new JMapFieldScanner<T>(this);
        for (JMapFieldScanner<T>.MethodInfo info : mapFieldScanner.findAnnotatedMethods()) {

            // Get info
            final org.jsimpledb.annotation.JMapField annotation = info.getAnnotation();
            final org.jsimpledb.annotation.JField keyAnnotation = annotation.key();
            final org.jsimpledb.annotation.JField valueAnnotation = annotation.value();
            final Method getter = info.getMethod();
            final String description = mapFieldScanner.getAnnotationDescription() + " annotation on method " + getter;
            final String fieldName = this.getFieldName(annotation.name(), info, description);
            this.log.debug("found " + description);

            // Get key and value types (the raw return type has already been validated by the annotation scanner)
            final TypeToken<?> keyType = this.getParameterType(description, getter, 0);
            final TypeToken<?> valueType = this.getParameterType(description, getter, 1);

            // Create key and value sub-fields
            final JSimpleField keyField = this.createSimpleField("key() property of " + description,
              keyType, null, keyAnnotation.type(), keyAnnotation.storageId(), keyAnnotation.indexed(),
              keyAnnotation.onDelete(), null, null,
              "key field of map field `" + fieldName + "' in object type `" + this.name + "'");
            final JSimpleField valueField = this.createSimpleField("value() property of " + description,
              valueType, null, valueAnnotation.type(), valueAnnotation.storageId(), valueAnnotation.indexed(),
              valueAnnotation.onDelete(), null, null,
              "value field of map field `" + fieldName + "' in object type `" + this.name + "'");

            // Create map field
            final JMapField jfield = new JMapField(fieldName, annotation.storageId(),
              keyField, valueField, "map field `" + fieldName + "' in object type `" + this.name + "'", getter);
            keyField.parent = jfield;
            valueField.parent = jfield;

            // Add field
            this.addField(jfield);
            this.log.debug("added map field `" + fieldName + "' to object type `" + this.name + "'");
        }
    }

    void scanAnnotations() {
        this.onCreateMethods = new OnCreateScanner<T>(this).findAnnotatedMethods();
        this.onDeleteMethods = new OnDeleteScanner<T>(this).findAnnotatedMethods();
        this.onChangeMethods = new OnChangeScanner<T>(this).findAnnotatedMethods();
        this.validateMethods = new ValidateScanner<T>(this).findAnnotatedMethods();
        final OnVersionChangeScanner<T> onVersionChangeScanner = new OnVersionChangeScanner<T>(this);
        this.onVersionChangeMethods = new ArrayList<>(onVersionChangeScanner.findAnnotatedMethods());
        Collections.sort(this.onVersionChangeMethods, onVersionChangeScanner);
        this.indexQueryMethods = new IndexQueryScanner<T>(this).findAnnotatedMethods();
    }

    @Override
    SchemaObject toSchemaItem() {
        final SchemaObject schemaObject = new SchemaObject();
        super.initialize(schemaObject);
        for (JField field : this.jfields.values())
            schemaObject.addSchemaField(field.toSchemaItem());
        return schemaObject;
    }

    // Add new JField (and sub-fields, if any), checking for name and storage ID conflicts
    private void addField(JField jfield) {

        // Check for storage ID conflict
        JField other = this.jfields.get(jfield.storageId);
        if (other != null) {
            throw new IllegalArgumentException("illegal duplicate use of storage ID "
              + jfield.storageId + " for both " + other + " and " + jfield);
        }
        this.jfields.put(jfield.storageId, jfield);

        // Check for name conflict
        if ((other = this.jfieldsByName.get(jfield.name)) != null)
            throw new IllegalArgumentException("illegal duplicate use of field name `" + jfield.name + "' in " + this);
        this.jfieldsByName.put(jfield.name, jfield);
    }

    // Get field name, deriving it from the getter property name if necessary
    private String getFieldName(String fieldName, AnnotationScanner<T, ?>.MethodInfo info, String description) {
        if (fieldName.length() > 0)
            return fieldName;
        try {
            return info.getMethodPropertyName();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + description + ": can't infer field name: " + e, e);
        }
    }

    // Get the n'th generic type parameter
    private TypeToken<?> getParameterType(String description, Method method, int index) {
        try {
            return Util.getTypeParameter(TypeToken.of(method.getGenericReturnType()), index);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + description + ": invalid method return type: " + e.getMessage(), e);
        }
    }

    // Create a simple field, either regular object field or sub-field of complex field
    private JSimpleField createSimpleField(String description, TypeToken<?> fieldTypeToken, String fieldName,
      String typeName, int storageId, boolean indexed, DeleteAction onDelete, Method getter, Method setter,
      String fieldDescription) {

        // Complex sub-field?
        final boolean isSubField = getter == null;

        // Empty type name same as null
        if (typeName.length() == 0)
            typeName = null;

        // See if field type encompasses one or more JClass types and is therefore a reference type
        boolean isReferenceType = false;
        for (JClass<?> jclass : this.jdb.jclasses.values()) {
            if (fieldTypeToken.isAssignableFrom(jclass.typeToken)) {
                isReferenceType = true;
                break;
            }
        }

        // See if field type is a simple type, known either by explicitly-given name or type
        FieldType<?> nonReferenceType = null;
        if (typeName != null) {

            // Field type is explicitly specified by name
            if ((nonReferenceType = this.jdb.db.getFieldTypeRegistry().getFieldType(typeName)) == null)
                throw new IllegalArgumentException("invalid " + description + ": unknown simple field type `" + typeName + "'");

            // Verify field type matches what we expect
            final TypeToken<?> expectedType = isSubField ? nonReferenceType.getTypeToken().wrap() : nonReferenceType.getTypeToken();
            if (!expectedType.equals(fieldTypeToken)) {
                throw new IllegalArgumentException("invalid " + description + ": field type `" + typeName
                  + "' supports values of type " + nonReferenceType.getTypeToken() + " but " + fieldTypeToken
                  + " is required (according to the getter method's return type)");
            }
        } else {

            // Try to find a field type supporting getter method return type
            try {
                nonReferenceType = this.jdb.db.getFieldTypeRegistry().getFieldType(fieldTypeToken);
            } catch (IllegalArgumentException e) {
                if (!isReferenceType) {
                    throw new IllegalArgumentException("invalid " + description + ": an explicit type() must be specified"
                      + " because type " + fieldTypeToken + " is supported by more than one registered simple field type", e);
                }
            }

            // Check for enum types
            if (nonReferenceType == null && Enum.class.isAssignableFrom(fieldTypeToken.getRawType()))
                nonReferenceType = this.jdb.db.getFieldTypeRegistry().getFieldType(TypeToken.of(EnumValue.class));
        }

        // If field type neither refers to a JClass type, or is a registered field type, fail
        if (!isReferenceType && nonReferenceType == null) {
            throw new IllegalArgumentException("invalid " + description + ": an explicit type() must be specified"
              + " because no known type supports values of type " + fieldTypeToken);
        }

        // Handle ambiguity between reference vs. non-reference
        if (isReferenceType && nonReferenceType != null) {

            // If an explicit type name was provided, assume they want the specified non-reference type
            if (typeName != null)
                isReferenceType = false;
            else {
                throw new IllegalArgumentException("invalid " + description + ": an explicit type() must be specified"
                  + " because type " + fieldTypeToken + " is ambiguous, being both a @" + JSimpleClass.class.getSimpleName()
                  + " reference type and a simple Java type supported by type `" + nonReferenceType + "'");
            }
        }

        // Create simple or reference field
        try {
            return isReferenceType ?
              new JReferenceField(fieldName, storageId, fieldDescription, fieldTypeToken, onDelete, getter, setter) :
              new JSimpleField(fieldName, storageId, fieldTypeToken,
                nonReferenceType.getName(), indexed, fieldDescription, getter, setter);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + description + ": " + e, e);
        }
    }
}

