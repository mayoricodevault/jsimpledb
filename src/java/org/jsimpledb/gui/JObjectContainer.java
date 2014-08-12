
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.gui;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;

import org.dellroad.stuff.vaadin7.PropertyDef;
import org.dellroad.stuff.vaadin7.PropertyExtractor;
import org.dellroad.stuff.vaadin7.ProvidesPropertyScanner;
import org.dellroad.stuff.vaadin7.SimpleItem;
import org.dellroad.stuff.vaadin7.SimpleKeyedContainer;
import org.jsimpledb.JCollectionField;
import org.jsimpledb.JCounterField;
import org.jsimpledb.JField;
import org.jsimpledb.JListField;
import org.jsimpledb.JMapField;
import org.jsimpledb.JObject;
import org.jsimpledb.JSimpleDB;
import org.jsimpledb.JSimpleField;
import org.jsimpledb.JTransaction;
import org.jsimpledb.SnapshotJTransaction;
import org.jsimpledb.change.Change;
import org.jsimpledb.change.ChangeAdapter;
import org.jsimpledb.change.FieldChange;
import org.jsimpledb.change.ObjectCreate;
import org.jsimpledb.change.ObjectDelete;
import org.jsimpledb.core.DeletedObjectException;
import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.ObjType;
import org.jsimpledb.core.UnknownFieldException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General purpose support superclass for Vaadin {@link com.vaadin.data.Container}s backed by {@link JSimpleDB} database objects.
 * Automatically creates properties for object ID, database type, version, and fields, as well as any custom properties
 * defined by {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated methods in Java model classes,
 * and handles {@link Change} updates of backing objects or their related objects.
 *
 * <p>
 * Instances are configured with a <b>type</b>, which can be any Java type (including interface types). The container
 * will then be restricted to database objects that are instances of the configured type. The type may be null, in which
 * case there is no restriction. The subclass method {@linkplain #queryForObjects queryForObjects()} determines which
 * objects are actually loaded into the container. The items in the container are backed by in-memory copies of the
 * corresponding database objects that live inside a {@link org.jsimpledb.SnapshotJTransaction}.
 * </p>
 *
 * <p><b>Container Properties</b></p>
 *
 * <p>
 * Instances will have the following container properties:
 * <ul>
 *  <li>{@link #OBJECT_ID_PROPERTY}: Object {@link ObjId}</li>
 *  <li>{@link #TYPE_PROPERTY}: Object type name (JSimpleDB type name, not Java type name, though these are typically the same)</li>
 *  <li>{@link #VERSION_PROPERTY}: Object schema version</li>
 *  <li>{@link #REFERENCE_LABEL_PROPERTY}: Object <b>reference label</b>, which is a short description identifying the
 *      object. Reference labels are used to provide "names" for objects that are more meaningful than object ID's
 *      and are used as such in other {@link JSimpleDB} GUI classes. To define the reference label for a Java model class,
 *      provide a {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated method
 *      (see below); otherwise, this property will return the same thing as {@link #OBJECT_ID_PROPERTY}.</i>
 *  <li>A property for every {@link JSimpleDB} field that is common to all object types that sub-type
 *      this instance's configured type. The property's ID is the field name; its value is as follows:
 *      <ul>
 *          <li>Simple fields will show their string values</li>
 *          <li>Reference fields show the {@link #REFERENCE_LABEL_PROPERTY} of the referred-to object, or "Null"
 *              if the reference is null</li>
 *          <li>Set, list, and map fields show the first few entries</li>
 *      </ul>
 *  </li>
 *  <li>A property for each {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated method
 *      in the specified <b>type</b>. These properties will add to, or override, the properties listed above.
 * </ul>
 * </p>
 *
 * <p><b>Loading</b></p>
 *
 * <p>
 * Instances may be (re)loaded at any time by invoking {@link #reload}. This causes the container
 * to query for {@link JObject}s within a new {@link JTransaction} via the subclass-provided method
 * {@link #queryForObjects queryForObjects()}, which returns an {@link Iterable Iterable<JObject>}. This container
 * copies the resulting database objects into the container's
 * in-memory {@link org.jsimpledb.SnapshotJTransaction}. The latter effectively serves as the cache for the container,
 * so that database transactions may be short-lived and are only required when (re)loading. Because the container contents
 * are in memory, with large data sets this container should only be loaded with one "page" of objects at a time (a "page"
 * typically being only what a human would be willing to scroll through at one time). It is up to
 * {@link #queryForObjects queryForObjects()} to determine what and how many objects are returned, their sort order, etc.
 * (if {@link #queryForObjects queryForObjects()} returns any objects that are not instances of the configured type,
 * they are ignored). In any case, the maximum number of objects that will be loaded is limited by a
 * {@linkplain #setMaxObjects configurable} maximum.
 * </p>
 *
 * <p>
 * When {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated methods are present on Java
 * model classes, these properyy values may derive from other objects related to the backing instance. In order for these
 * methods to work on the in-memory instance, the related objects must be copied into memory as well. The
 * {@link #getRelatedObjects getRelatedObjects()} method allows the subclass to specify what objects relate to each
 * backing object; the default behavior of delegating to {@link JObject#getRelatedObjects} is usually best, because
 * it allows the backing object itself, rather than the container, to specify related objects.
 * </p>
 *
 * <p><b>Updating Items</b></p>
 *
 * <p>
 * Instances support updating individual items without requiring a complete container reload by invoking
 * {@link #handleChange handleChange()}. In a typical design pattern, {@link org.jsimpledb.change.Change} objects originate
 * from {@link org.jsimpledb.annotation.OnChange &#64;OnChange} methods and are broadcast (after copying into memory using
 * a {@link org.jsimpledb.change.ChangeCopier}) to listeners such as this container by a transaction synchronization callback
 * (see {@link org.jsimpledb.core.Transaction#addCallback Transaction.addCallback()}).
 * </p>
 *
 * <p>
 * Moreover, this container keeps track of which related objects are associated with each backing object.
 * {@link Change} events that affect a related object automatically cause the corresponding backing object(s) to update.
 * </p>
 *
 * <p>
 * This class responds to the creation or deletion of a database object matching this container's configured type
 * by reloading the container; however, this is a conservative approach, as not necessarily every newly created (or
 * deleted) object should be (or was) present in the container in the first place. Subclasses may wish to optimize this
 * behavior by overriding {@link #handleObjectCreate handleObjectCreate()} and/or {@link #handleObjectDelete handleObjectDelete()}.
 * </p>
 *
 * <p><b>{@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty} Limitations</b></p>
 *
 * <p>
 * The use of {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty} methods has certain implications.
 * First, if the method reads any of the object's field(s) via their Java getter methods (as would normally be expected),
 * this will trigger a schema upgrade of the object if needed; however, this schema upgrade will occur in the
 * container's in-memory {@link org.jsimpledb.SnapshotJTransaction} rather than in a real database transaction, so the
 * {@link #VERSION_PROPERTY} will return a different schema version from what's in the database. The automatic schema
 * upgrade can be avoided if desired by reading the field using the appropriate {@link JTransaction} field access method
 * (e.g., {@link JTransaction#readSimpleField JTransaction.readSimpleField()}) and being prepared to handle a
 * {@link org.jsimpledb.core.UnknownFieldException} if/when the object has an older schema version that does not contain
 * the requested field.
 * </p>
 *
 * <p>
 * Secondly, because the values of reference fields (including complex sub-fields) are displayed using reference labels,
 * and these are typically derived from the referenced object's fields, those indirectly referenced objects need to be
 * copied into the container's {@link org.jsimpledb.SnapshotJTransaction} as well. The easiest way to ensure these indirectly
 * referenced objects are copied is by overriding {@link JObject#getRelatedObjects} as described above. Alternately,
 * the subclass may override {@link #copyOut copyOut()} to take responsibility for copying objects into memory.
 * </p>
 */
@SuppressWarnings("serial")
public abstract class JObjectContainer extends SimpleKeyedContainer<ObjId, JObject> {

    /**
     * Default maximum number of objects.
     */
    public static final int DEFAULT_MAX_OBJECTS = 1000;

    /**
     * Container property name for the reference label property, which has type {@link Component}.
     */
    public static final String REFERENCE_LABEL_PROPERTY = "$label";

    /**
     * Container property name for the object ID property.
     */
    public static final String OBJECT_ID_PROPERTY = "$objId";

    /**
     * Container property name for the object type property.
     */
    public static final String TYPE_PROPERTY = "$type";

    /**
     * Container property name for the object schema version property.
     */
    public static final String VERSION_PROPERTY = "$version";

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * The associated {@link JSimpleDB}, provding schema information.
     */
    protected final JSimpleDB jdb;

    private final ObjIdPropertyDef objIdPropertyDef = new ObjIdPropertyDef();
    private final ObjTypePropertyDef objTypePropertyDef = new ObjTypePropertyDef();
    private final ObjVersionPropertyDef objVersionPropertyDef = new ObjVersionPropertyDef();
    private final RefLabelPropertyDef refLabelPropertyDef = new RefLabelPropertyDef();

    private final HashMap<ObjId, HashSet<ObjId>> relatedObjectMap = new HashMap<>();

    private Class<?> type;
    private int maxObjects = DEFAULT_MAX_OBJECTS;
    private ProvidesPropertyScanner<?> propertyScanner;
    private List<String> orderedPropertyNames;

    /**
     * Constructor.
     *
     * @param jdb {@link JSimpleDB} database
     * @param type type restriction, or null for no restriction
     * @throws IllegalArgumentException if {@code jdb} is null
     */
    protected <T> JObjectContainer(JSimpleDB jdb, Class<T> type) {
        if (jdb == null)
            throw new IllegalArgumentException("null jdb");
        this.jdb = jdb;
        this.setType(type);
        this.setPropertyExtractor(this);
    }

    /**
     * Get the type restriction associated with this instance.
     *
     * @return Java type restriction, or null if there is none
     */
    public Class<?> getType() {
        return this.type;
    }

    /**
     * Change the type restriction associated with this instance. Typically requires a reload.
     *
     * @param type Java type restriction, or null for none
     */
    public <T> void setType(Class<T> type) {
        this.type = type;
        this.propertyScanner = this.type != null ? new ProvidesPropertyScanner<T>(/*this.*/type) : null;
        final ArrayList<PropertyDef<?>> propertyDefs = new ArrayList<>(this.buildPropertyDefs());
        this.orderedPropertyNames = Collections.unmodifiableList(Lists.transform(propertyDefs,
          new Function<PropertyDef<?>, String>() {
            @Override
            public String apply(PropertyDef<?> propertyDef) {
                return propertyDef.getName();
            }
        }));
        this.setProperties(propertyDefs);
    }

    /**
     * Configure the maximum number of objects.
     */
    public void setMaxObjects(int maxObjects) {
        this.maxObjects = Math.max(maxObjects, 0);
    }

    /**
     * Get the properties of this container in preferred order.
     */
    public List<String> getOrderedPropertyNames() {
        return this.orderedPropertyNames;
    }

    @Override
    public ObjId getKeyFor(JObject jobj) {
        return jobj.getObjId();
    }

    /**
     * (Re)load this container.
     *
     * <p>
     * This method delegates to {@link #queryForObjects queryForObjects()} indirectly via {@link #doInTransaction}.
     * </p>
     */
    public void reload() {
        this.doInTransaction(new Runnable() {
            @Override
            public void run() {
                JObjectContainer.this.doReloadInTransaction();
            }
        });
    }

    /**
     * Apply the given change to this container, if applicable.
     *
     * <p>
     * The changed object {@linkplain Change#getObject referred to} by {@code change} must be accessible.
     * Unless this method is invoked from within the transaction in which the change occurred, this means that
     * {@code change} must have been be copied out of its transaction, e.g, using a {@link org.jsimpledb.change.ChangeCopier}.
     * </p>
     *
     * <p>
     * The implementation in {@link JObjectContainer} delegates to {@link #handleObjectCreate handleObjectCreate()},
     * {@link #handleObjectDelete handleObjectDelete()}, or {@link #handleFieldChange handleFieldChange()} as appropriate.
     * </p>
     *
     * @throws IllegalArgumentException if {@code change} is null
     * @throws org.jsimpledb.core.StaleTransactionException if {@code change} refers to a {@link JObject}
     *  without an associated tranasction.
     */
    public <T> void handleChange(Change<T> change) {

        // Sanity check
        if (change == null)
            throw new IllegalArgumentException("null change");

        // Determine whether the change concerns us
        if (this.type != null && !this.type.isInstance(change.getObject()))
            return;

        // Apply change
        change.visit(new ChangeAdapter<Void>() {

            @Override
            public <T> Void caseObjectDelete(ObjectDelete<T> change) {
                JObjectContainer.this.handleObjectDelete(change);
                return null;
            }

            @Override
            public <T> Void caseObjectCreate(ObjectCreate<T> change) {
                JObjectContainer.this.handleObjectCreate(change);
                return null;
            }

            @Override
            protected <T> Void caseFieldChange(FieldChange<T> change) {
                JObjectContainer.this.handleFieldChange(change);
                return null;
            }
        });
    }

    /**
     * Handle an {@link ObjectCreate} event.
     *
     * <p>
     * The implementation in {@link JObjectContainer} {@linkplain #reload reloads} the container.
     * </p>
     */
    protected <T> void handleObjectCreate(ObjectCreate<T> change) {
        this.reload();
    }

    /**
     * Handle an {@link ObjectDelete} event.
     *
     * <p>
     * The implementation in {@link JObjectContainer} {@linkplain #reload reloads} the container.
     * </p>
     */
    protected <T> void handleObjectDelete(ObjectDelete<T> change) {
        this.reload();
    }

    /**
     * Handle a {@link FieldChange} event.
     *
     * <p>
     * The implementation in {@link JObjectContainer} triggers value change events for all affected item(s) in this container.
     * Affected items are those whose backing object is either the changed object, or has the changed object as a related object.
     * </p>
     */
    protected <T> void handleFieldChange(FieldChange<T> change) {

        // Update the matching backing object, if any
        final JObject target = change.getJObject();
        final SimpleItem<JObject> item = (SimpleItem<JObject>)this.getItem(target.getObjId());
        if (item != null) {
            target.copyTo(item.getObject().getTransaction(), null);
            item.fireValueChange();
        }

        // Update any other affected objects
        final HashSet<ObjId> affectedIds = relatedObjectMap.get(target.getObjId());
        if (affectedIds != null) {
            for (ObjId affectedId : affectedIds) {
                final SimpleItem<JObject> affectedItem = (SimpleItem<JObject>)this.getItem(affectedId);
                if (affectedItem != null)
                    affectedItem.fireValueChange();
            }
        }
    }

    // This method runs within a transactin
    private void doReloadInTransaction() {

        // Get objects from subclass
        Iterable<? extends JObject> jobjs = this.queryForObjects();

        // Filter out any instances of the wrong type
        if (JObjectContainer.this.type != null) {
            jobjs = Iterables.filter(jobjs, new Predicate<JObject>() {
                @Override
                public boolean apply(JObject jobj) {
                    return JObjectContainer.this.type.isInstance(jobj);
                }
            });
        }

        // Filter out any duplicate objects
        jobjs = Iterables.filter(jobjs, new Predicate<JObject>() {

            private final HashSet<ObjId> seen = new HashSet<>();

            @Override
            public boolean apply(JObject jobj) {
                return this.seen.add(jobj.getObjId());
            }
        });

        // Copy database objects out of the database transaction into my in-memory transaction as we load them
        jobjs = Iterables.transform(jobjs, new Function<JObject, JObject>() {
            @Override
            public JObject apply(JObject jobj) {
                return JObjectContainer.this.copyOut(jobj);
            }
        });

        // Limit the total number of objects in the container
        jobjs = Iterables.limit(jobjs, this.maxObjects);

        // Now actually load them
        this.relatedObjectMap.clear();
        this.load(jobjs);
    }

    /**
     * Copy the given database object, and any related objects needed by any
     * {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated methods,
     * into the current {@link org.jsimpledb.SnapshotJTransaction}.
     *
     * <p>
     * The implementation in {@link JObjectContainer} copies {@code jobj}, and all of {@code jobj}'s related objects returned
     * by {@link #getRelatedObjects getRelatedObjects()},
     * via {@link JTransaction#copyTo(JTransaction, Iterable)} and updates the
     * </p>
     *
     * @param target the object to copy
     * @return the copy of {@code target} in the current {@link org.jsimpledb.SnapshotJTransaction}
     */
    protected JObject copyOut(JObject target) {

        // Copy out object
        final ObjId targetId = target.getObjId();
        final JTransaction jtx = JTransaction.getCurrent();
        final JObject copy = target.copyOut();

        // Copy out (and track) related objects
        Iterable<? extends JObject> relatedObjects = this.getRelatedObjects(target);
        if (relatedObjects != null) {
            relatedObjects = Iterables.filter(relatedObjects, new Predicate<JObject>() {
                @Override
                public boolean apply(JObject jobj) {
                    return jobj != null                                                         // don't copy nulls
                      && !jobj.getObjId().equals(targetId)                                      // already copied it
                      && !JObjectContainer.this.relatedObjectMap.containsKey(jobj.getObjId());  // already copied it
                }
            });
            final SnapshotJTransaction sjtx = jtx.getSnapshotTransaction();
            jtx.copyTo(sjtx, relatedObjects);
            for (JObject jobj : relatedObjects) {
                final ObjId id = jobj.getObjId();
                HashSet<ObjId> affectedIds = this.relatedObjectMap.get(id);
                if (affectedIds == null) {
                    affectedIds = new HashSet<ObjId>();
                    this.relatedObjectMap.put(id, affectedIds);
                }
                affectedIds.add(targetId);
            }
        }

        // Done
        return copy;
    }

    /**
     * Find objects related to the specified object that are needed by any
     * {@link org.dellroad.stuff.vaadin7.ProvidesProperty &#64;ProvidesProperty}-annotated
     * methods into the current {@link org.jsimpledb.SnapshotJTransaction}. This effectively defines
     * all of the other objects on which any container property of {@code jobj} may depend.
     *
     * <p>
     * The implementation in {@link JObjectContainer} just delegates to
     * {@link JObject#getRelatedObjects JObject.getRelatedObjects()}.
     * </p>
     *
     * @param jobj the object being copied
     * @return {@link Iterable} of additional objects to be copied, or null for none; any null values are ignored
     */
    protected Iterable<? extends JObject> getRelatedObjects(JObject jobj) {
        return jobj.getRelatedObjects();
    }

    /**
     * Perform the given action within a {@link JTransaction}.
     *
     * @param action the action to perform
     */
    protected abstract void doInTransaction(Runnable action);

    /**
     * Query for the database objects that will be used to fill this container. Objects should be returned in the
     * desired order; any duplicates will be ignored.
     *
     * <p>
     * A {@link JTransaction} will be open in the current thread when this method is invoked.
     * </p>
     *
     * @return database objects
     */
    protected abstract Iterable<? extends JObject> queryForObjects();

// Property derivation

    private Collection<PropertyDef<?>> buildPropertyDefs() {
        final PropertyDefHolder pdefs = new PropertyDefHolder();

        // Add properties shared by all JObjects
        pdefs.setPropertyDef(this.refLabelPropertyDef);
        pdefs.setPropertyDef(this.objIdPropertyDef);
        pdefs.setPropertyDef(this.objTypePropertyDef);
        pdefs.setPropertyDef(this.objVersionPropertyDef);

        // Add properties for all fields common to all sub-types of our configured type
        final SortedMap<Integer, JField> jfields = Util.getCommonJFields(
          this.jdb.getJClasses(this.type != null ? TypeToken.of(this.type) : null));
        final HashSet<String> fieldNames = new HashSet<>();
        if (jfields != null) {
            for (JField jfield : jfields.values()) {
                fieldNames.add(jfield.getName());
                pdefs.setPropertyDef(new ObjFieldPropertyDef(jfield));
            }
        }

        // Apply any @ProvidesProperty-annotated method properties, possibly overridding jfields
        if (this.propertyScanner != null) {
            for (PropertyDef<?> propertyDef : this.propertyScanner.getPropertyDefs())
                pdefs.setPropertyDef(propertyDef);
        }

        // Done
        return pdefs.values();
    }

// PropertyExtractor

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getPropertyValue(JObject jobj, PropertyDef<V> propertyDef) {
        if (propertyDef instanceof ObjPropertyDef)
            return (V)((ObjPropertyDef<?>)propertyDef).extract(jobj);
        if (this.propertyScanner == null)
            throw new IllegalArgumentException("unknown property: " + propertyDef.getName());
        return JObjectContainer.extractProperty(this.propertyScanner.getPropertyExtractor(), propertyDef, jobj);
    }

    @SuppressWarnings("unchecked")
    private static <V> V extractProperty(PropertyExtractor<?> propertyExtractor, PropertyDef<V> propertyDef, JObject jobj) {
        try {

            // try/catch is a workaround for a dellroad-stuff bug which is fixed in r907
            try {
                return ((PropertyExtractor<JObject>)propertyExtractor).getPropertyValue(jobj, propertyDef);
            } catch (RuntimeException e) {
                if (e.getCause() instanceof InvocationTargetException) {
                    final InvocationTargetException e2 = (InvocationTargetException)e.getCause();
                    if (e2.getCause() instanceof RuntimeException)
                        throw (RuntimeException)e2.getCause();
                    if (e2.getCause() instanceof Error)
                        throw (Error)e2.getCause();
                }
                throw e;
            }
        } catch (DeletedObjectException e) {
            try {
                return propertyDef.getType().cast(new SizedLabel("<i>Missing</i>", ContentMode.HTML));
            } catch (ClassCastException e2) {
                try {
                    return propertyDef.getType().cast("(Missing)");
                } catch (ClassCastException e3) {
                    return null;
                }
            }
        }
    }

// ObjPropertyDef

    /**
     * Support superclass for {@link PropertyDef} implementations that derive the property value from a {@link JObject}.
     */
    public abstract static class ObjPropertyDef<T> extends PropertyDef<T> {

        protected ObjPropertyDef(String name, Class<T> type) {
            super(name, type);
        }

        public abstract T extract(JObject jobj);
    }

// ObjIdPropertyDef

    /**
     * Implements the {@link JObjectContainer#OBJECT_ID_PROPERTY} property.
     */
    public static class ObjIdPropertyDef extends ObjPropertyDef<SizedLabel> {

        public ObjIdPropertyDef() {
            super(OBJECT_ID_PROPERTY, SizedLabel.class);
        }

        @Override
        public SizedLabel extract(JObject jobj) {
            return new SizedLabel("<code>" + jobj.getObjId() + "</code>", ContentMode.HTML);
        }
    }

// ObjTypePropertyDef

    /**
     * Implements the {@link JObjectContainer#TYPE_PROPERTY} property.
     */
    public static class ObjTypePropertyDef extends ObjPropertyDef<SizedLabel> {

        public ObjTypePropertyDef() {
            super(TYPE_PROPERTY, SizedLabel.class);
        }

        @Override
        public SizedLabel extract(JObject jobj) {
            return new SizedLabel(jobj.getTransaction().getTransaction().getSchema()
              .getVersion(jobj.getSchemaVersion()).getSchemaItem(jobj.getObjId().getStorageId(), ObjType.class).getName());
        }
    }

// ObjVersionPropertyDef

    /**
     * Implements the {@link JObjectContainer#VERSION_PROPERTY} property.
     */
    public static class ObjVersionPropertyDef extends ObjPropertyDef<SizedLabel> {

        public ObjVersionPropertyDef() {
            super(VERSION_PROPERTY, SizedLabel.class);
        }

        @Override
        public SizedLabel extract(JObject jobj) {
            return new SizedLabel("" + jobj.getSchemaVersion());
        }
    }

// RefLabelPropertyDef

    /**
     * Implements the {@link JObjectContainer#REFERENCE_LABEL_PROPERTY} property.
     */
    public static class RefLabelPropertyDef extends ObjPropertyDef<Component> {

        public RefLabelPropertyDef() {
            super(REFERENCE_LABEL_PROPERTY, Component.class);
        }

        @Override
        public Component extract(JObject jobj) {
            final ReferenceMethodInfoCache.PropertyInfo<?> propertyInfo
              = ReferenceMethodInfoCache.getInstance().getReferenceMethodInfo(jobj.getClass());
            if (propertyInfo == ReferenceMethodInfoCache.NOT_FOUND)
                return new ObjIdPropertyDef().extract(jobj);
            final Object value = JObjectContainer.extractProperty(
              propertyInfo.getPropertyExtractor(), propertyInfo.getPropertyDef(), jobj);
            if (value instanceof Component)
                return (Component)value;
            return new SizedLabel(String.valueOf(value));
        }
    }

// ObjFieldPropertyDef

    /**
     * Implements a property reflecting the value of a {@link JSimpleDB} field.
     */
    public static class ObjFieldPropertyDef extends ObjPropertyDef<Component> {

        private static final int MAX_ITEMS = 3;

        private final JField jfield;

        public ObjFieldPropertyDef(JField jfield) {
            super(jfield.getName(), Component.class);
            this.jfield = jfield;
        }

        @Override
        public Component extract(final JObject jobj) {
            final JTransaction jtx = jobj.getTransaction();
            try {
                if (this.jfield instanceof JSimpleField)
                    return this.handleValue(this.jfield.getValue(jtx, jobj));
                else if (this.jfield instanceof JCounterField)
                    return this.handleValue(((JCounterField)this.jfield).getValue(jtx, jobj).get());
                else if (this.jfield instanceof JCollectionField)
                    return this.handleCollectionField(((JCollectionField)this.jfield).getValue(jtx, jobj));
                else if (this.jfield instanceof JListField)
                    return this.handleCollectionField(((JListField)this.jfield).getValue(jtx, jobj));
                else if (this.jfield instanceof JMapField) {
                    final NavigableMap<?, ?> map = ((JMapField)this.jfield).getValue(jtx, jobj);
                    return this.handleMultiple(Iterables.transform(map.entrySet(), new Function<Map.Entry<?, ?>, Component>() {
                        @Override
                        public Component apply(Map.Entry<?, ?> entry) {
                            final HorizontalLayout layout = new HorizontalLayout();
                            layout.setMargin(false);
                            layout.setSpacing(false);
                            layout.addComponent(ObjFieldPropertyDef.this.handleValue(entry.getKey()));
                            layout.addComponent(new SizedLabel(" \u21d2 "));        // RIGHTWARDS DOUBLE ARROW
                            layout.addComponent(ObjFieldPropertyDef.this.handleValue(entry.getValue()));
                            return layout;
                        }
                    }));
                } else
                    throw new RuntimeException("internal error");
            } catch (UnknownFieldException e) {
                return new SizedLabel("<i>NA</i>", ContentMode.HTML);
            }
        }

        private Component handleCollectionField(Collection<?> col) {
            return this.handleMultiple(Iterables.transform(col, new Function<Object, Component>() {
                @Override
                public Component apply(Object item) {
                    return ObjFieldPropertyDef.this.handleValue(item);
                }
            }));
        }

        private Component handleMultiple(Iterable<Component> components) {
            final HorizontalLayout layout = new HorizontalLayout();
            layout.setMargin(false);
            layout.setSpacing(false);
            int count = 0;
            for (Component component : components) {
                if (count >= MAX_ITEMS) {
                    layout.addComponent(new SizedLabel("..."));
                    break;
                }
                if (count > 0)
                    layout.addComponent(new SizedLabel(",&#160;", ContentMode.HTML));
                layout.addComponent(component);
                count++;
            }
            return layout;
        }

        @SuppressWarnings("unchecked")
        private <T> Component handleValue(Object value) {
            if (value == null)
                return new SizedLabel("<i>Null</i>", ContentMode.HTML);
            if (value instanceof JObject)
                return new RefLabelPropertyDef().extract((JObject)value);
            return new SizedLabel(String.valueOf(value));
        }
    }

// PropertyDefHolder

    private static class PropertyDefHolder extends LinkedHashMap<String, PropertyDef<?>> {

        public void setPropertyDef(PropertyDef<?> propertyDef) {
            this.put(propertyDef.getName(), propertyDef);
        }
    }
}
