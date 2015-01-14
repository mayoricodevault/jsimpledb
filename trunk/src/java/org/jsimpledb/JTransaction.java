
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb;

import com.google.common.base.Converter;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.validation.ConstraintViolation;

import org.dellroad.stuff.validation.ValidationUtil;
import org.jsimpledb.core.CoreIndex;
import org.jsimpledb.core.CoreIndex2;
import org.jsimpledb.core.CoreIndex3;
import org.jsimpledb.core.CreateListener;
import org.jsimpledb.core.DeleteListener;
import org.jsimpledb.core.DeletedObjectException;
import org.jsimpledb.core.EnumField;
import org.jsimpledb.core.Field;
import org.jsimpledb.core.FieldSwitchAdapter;
import org.jsimpledb.core.ListField;
import org.jsimpledb.core.MapField;
import org.jsimpledb.core.ObjId;
import org.jsimpledb.core.ObjType;
import org.jsimpledb.core.ReferenceField;
import org.jsimpledb.core.SchemaVersion;
import org.jsimpledb.core.SetField;
import org.jsimpledb.core.SimpleField;
import org.jsimpledb.core.StaleTransactionException;
import org.jsimpledb.core.Transaction;
import org.jsimpledb.core.TypeNotInSchemaVersionException;
import org.jsimpledb.core.UnknownFieldException;
import org.jsimpledb.core.VersionChangeListener;
import org.jsimpledb.index.Index;
import org.jsimpledb.index.Index2;
import org.jsimpledb.index.Index3;
import org.jsimpledb.kv.KeyRanges;
import org.jsimpledb.kv.util.AbstractKVNavigableSet;
import org.jsimpledb.util.ConvertedNavigableMap;
import org.jsimpledb.util.ConvertedNavigableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transaction associated with a {@link JSimpleDB} instance.
 *
 * <p>
 * Commonly used methods in this class can be divided into the following categories:
 * </p>
 *
 * <p>
 * <b>Transaction Meta-Data</b>
 * <ul>
 *  <li>{@link #getJSimpleDB getJSimpleDB()} - Get the associated {@link JSimpleDB} instance</li>
 *  <li>{@link #getTransaction} - Get the core API {@link Transaction} underlying this instance</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Transaction Lifecycle</b>
 * <ul>
 *  <li>{@link #commit commit()} - Commit transaction</li>
 *  <li>{@link #rollback rollback()} - Roll back transaction</li>
 *  <li>{@link #getCurrent getCurrent()} - Get the {@link JTransaction} instance associated with the current thread</li>
 *  <li>{@link #setCurrent setCurrent()} - Set the {@link JTransaction} instance associated with the current thread</li>
 *  <li>{@link #isValid isValid()} - Test transaction validity</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Object Access</b>
 * <ul>
 *  <li>{@link #getJObject(ObjId) getJObject()} - Get the Java model object corresponding to a specific database object ID</li>
 *  <li>{@link #create(Class) create()} - Create a new database object</li>
 *  <li>{@link #getAll getAll()} - Get all database objects that are instances of a given Java type</li>
 *  <li>{@link #queryVersion queryVersion()} - Get database objects grouped according to their schema versions</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Validation</b>
 * <ul>
 *  <li>{@link #validate validate()} - Validate all objects in the validation queue</li>
 *  <li>{@link #resetValidationQueue} - Clear the validation queue</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Index Queries</b>
 * <ul>
 *  <li>{@link #queryIndex(Class, String, Class) queryIndex()}
 *      - Access the index associated with a simple field</li>
 *  <li>{@link #queryListElementIndex queryListElementIndex()}
 *      - Access the composite index associated with a list field that includes corresponding list indicies</li>
 *  <li>{@link #queryMapValueIndex queryMapValueIndex()}
 *      - Access the composite index associated with a map value field that includes corresponding map keys</li>
 *  <li>{@link #queryCompositeIndex(Class, String, Class, Class) queryCompositeIndex()}
 *      - Access a composite index defined on two fields</li>
 *  <li>{@link #queryCompositeIndex(Class, String, Class, Class, Class) queryCompositeIndex()}
 *      - Access a composite index defined on three fields</li>
 *  <!-- COMPOSITE-INDEX -->
 * </ul>
 * </p>
 *
 * <p>
 * <b>Reference Inversion</b>
 * <ul>
 *  <li>{@link #invertReferencePath invertReferencePath()} - Find all objects that refer to any element in a given set
 *      of objects through a specified reference path</li>
 *  </li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Snapshot Transactions</b>
 * <ul>
 *  <li>{@link #getSnapshotTransaction getSnapshotTransaction()} - Get the default in-memory transaction
 *      associated with this transaction</li>
 *  <li>{@link #copyTo(JTransaction, JObject, ObjId, ObjIdSet, String[]) copyTo()} - Copy an object into another transaction</li>
 *  <li>{@link #copyTo(JTransaction, ObjIdSet, Iterable) copyTo()} - Copy explicitly specified objects into another transaction</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Lower Layer Access</b>
 * <ul>
 *  <li>{@link #getKey(JObject) getKey()} - Get the {@link org.jsimpledb.kv.KVDatabase} key prefix for a specific object</li>
 *  <li>{@link #getKey(JObject, String) getKey()} - Get the {@link org.jsimpledb.kv.KVDatabase}
 *      key for a specific field in a specific object</li>
 * </ul>
 * </p>
 *
 * <p>
 * The remaining methods in this class are normally only used by generated Java model object subclasses.
 * Instead of using these methods directly, using the appropriately annotated Java model object method
 * or {@link JObject} interface method is recommended.
 * </p>
 *
 * <p>
 * <b>Java Model Object Methods</b>
 * <ul>
 *  <li>{@link #readSimpleField readSimpleField()} - Read the value of a simple field</li>
 *  <li>{@link #writeSimpleField writeSimpleField()} - Write the value of a simple field</li>
 *  <li>{@link #readCounterField readCounterField()} - Access a {@link Counter} field</li>
 *  <li>{@link #readSetField readSetField()} - Access a set field</li>
 *  <li>{@link #readListField readListField()} - Access a list field</li>
 *  <li>{@link #readMapField readMapField()} - Access a map field</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>{@link JObject} Methods</b>
 * <ul>
 *  <li>{@link #delete delete()} - Delete an object from this transaction</li>
 *  <li>{@link #exists exists()} - Test whether an object exists in this transaction</li>
 *  <li>{@link #recreate recreate()} - Recreate an object in this transaction</li>
 *  <li>{@link #revalidate revalidate()} - Add an object to the validation queue</li>
 *  <li>{@link #getSchemaVersion getSchemaVersion()} - Get this schema version of an object</li>
 *  <li>{@link #updateSchemaVersion updateSchemaVersion()} - Update an object's schema version</li>
 * </ul>
 * </p>
 */
public class JTransaction {

    private static final ThreadLocal<JTransaction> CURRENT = new ThreadLocal<>();

    final Logger log = LoggerFactory.getLogger(this.getClass());

    final JSimpleDB jdb;
    final Transaction tx;

    private final ValidationMode validationMode;
    private final ValidationListener validationListener = new ValidationListener();
    private final InternalCreateListener internalCreateListener = new InternalCreateListener();
    private final InternalDeleteListener internalDeleteListener = new InternalDeleteListener();
    private final InternalVersionChangeListener internalVersionChangeListener = new InternalVersionChangeListener();
    private final ObjIdSet validationQueue = new ObjIdSet();

    private final LoadingCache<IndexInfoKey, IndexInfo> indexInfoCache = CacheBuilder.newBuilder()
      .maximumSize(1000).build(new CacheLoader<IndexInfoKey, IndexInfo>() {
        @Override
        public IndexInfo load(IndexInfoKey key) {
            return key.getIndexInfo(JTransaction.this.jdb);
        }
      });

    private SnapshotJTransaction snapshotTransaction;
    private boolean committing;

// Constructor

    /**
     * Constructor.
     *
     * @throws IllegalArgumentException if any parameter is null
     */
    JTransaction(JSimpleDB jdb, Transaction tx, ValidationMode validationMode) {

        // Initialization
        if (jdb == null)
            throw new IllegalArgumentException("null jdb");
        if (tx == null)
            throw new IllegalArgumentException("null tx");
        if (validationMode == null)
            throw new IllegalArgumentException("null validationMode");
        this.jdb = jdb;
        this.tx = tx;
        this.validationMode = validationMode;

        // Register listeners for @OnCreate
        if (this.jdb.hasOnCreateMethods || validationMode == ValidationMode.AUTOMATIC)
            this.tx.addCreateListener(this.internalCreateListener);

        // Register listeners for @OnDelete
        if (this.jdb.hasOnDeleteMethods)
            this.tx.addDeleteListener(this.internalDeleteListener);

        // Register listeners for @OnChange
        for (JClass<?> jclass : this.jdb.jclasses.values()) {
            for (OnChangeScanner<?>.MethodInfo info : jclass.onChangeMethods) {
                if (this instanceof SnapshotJTransaction && !info.getAnnotation().snapshotTransactions())
                    continue;
                final OnChangeScanner<?>.ChangeMethodInfo changeInfo = (OnChangeScanner<?>.ChangeMethodInfo)info;
                changeInfo.registerChangeListener(this);
            }
        }

        // Register listeners for @Validate and JSR 303 constraints
        if (validationMode == ValidationMode.AUTOMATIC) {
            for (JFieldInfo jfieldInfo : this.jdb.jfieldInfos.values()) {
                if (jfieldInfo.isRequiresValidation())
                    jfieldInfo.registerChangeListener(this.tx, new int[0], null, this.validationListener);
            }
        }

        // Register listeners for @OnVersionChange
        if (this.jdb.hasOnVersionChangeMethods)
            this.tx.addVersionChangeListener(this.internalVersionChangeListener);
    }

// Thread-local Access

    /**
     * Get the {@link JTransaction} associated with the current thread, if any, otherwise throw an exception.
     *
     * @return instance previously associated with the current thread via {@link #setCurrent setCurrent()}
     * @throws IllegalStateException if there is no such instance
     */
    public static JTransaction getCurrent() {
        final JTransaction jtx = CURRENT.get();
        if (jtx == null) {
            throw new IllegalStateException("there is no " + JTransaction.class.getSimpleName()
              + " associated with the current thread");
        }
        return jtx;
    }

    /**
     * Set the {@link JTransaction} associated with the current thread.
     */
    public static void setCurrent(JTransaction jtransaction) {
        CURRENT.set(jtransaction);
    }

// Accessors

    /**
     * Get the {@link JSimpleDB} associated with this instance.
     */
    public JSimpleDB getJSimpleDB() {
        return this.jdb;
    }

    /**
     * Get the {@link Transaction} associated with this instance.
     */
    public Transaction getTransaction() {
        return this.tx;
    }

    /**
     * Get the {@link ValidationMode} configured for this instance.
     */
    public ValidationMode getValidationMode() {
        return this.validationMode;
    }

    /**
     * Get all instances of the given type.
     *
     * @param type any Java type; use {@link Object Object.class} to return all database objects
     * @return read-only view of all instances of {@code type}
     * @throws IllegalArgumentException if {@code type} is null
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings("unchecked")
    public <T> NavigableSet<T> getAll(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        NavigableSet<ObjId> ids = this.tx.getAll();
        final KeyRanges keyRanges = this.jdb.keyRangesFor(type);
        if (!keyRanges.isFull())
            ids = ((AbstractKVNavigableSet<ObjId>)ids).filterKeys(keyRanges);
        return new ConvertedNavigableSet<T, ObjId>(ids, new ReferenceConverter<T>(this, type));
    }

    /**
     * Get all instances of the given type, grouped according to schema version.
     *
     * @param type any Java type; use {@link Object Object.class} to return all database objects
     * @throws IllegalArgumentException if {@code type} is null
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    public <T> NavigableMap<Integer, NavigableSet<T>> queryVersion(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        CoreIndex<Integer, ObjId> index = this.tx.queryVersion();
        final KeyRanges keyRanges = this.jdb.keyRangesFor(type);
        if (!keyRanges.isFull())
            index = index.filter(1, keyRanges);
        return new ConvertedNavigableMap<Integer, NavigableSet<T>, Integer, NavigableSet<ObjId>>(index.asMap(),
          Converter.<Integer>identity(), new NavigableSetConverter<T, ObjId>(new ReferenceConverter<T>(this, type)));
    }

    /**
     * Get the {@code byte[]} key in the underlying key/value store corresponding to the specified object.
     *
     * <p>
     * Notes:
     * <ul>
     *  <li>Objects utilize mutiple keys; the return value is the common prefix of all such keys.</li>
     *  <li>The {@link org.jsimpledb.kv.KVDatabase} should not be modified directly, otherwise behavior is undefined</li>
     * </ul>
     * </p>
     *
     * @param jobj Java model object
     * @return the {@link org.jsimpledb.kv.KVDatabase} key corresponding to {@code jobj}
     * @throws IllegalArgumentException if {@code jobj} is null
     */
    public byte[] getKey(JObject jobj) {
        if (jobj == null)
            throw new IllegalArgumentException("null jobj");
        return this.tx.getKey(jobj.getObjId());
    }

    /**
     * Get the {@code byte[]} key in the underlying key/value store corresponding to the specified field in the specified object.
     *
     * <p>
     * Notes:
     * <ul>
     *  <li>Complex fields utilize mutiple keys; the return value is the common prefix of all such keys.</li>
     *  <li>The {@link org.jsimpledb.kv.KVDatabase} should not be modified directly, otherwise behavior is undefined</li>
     * </ul>
     * </p>
     *
     * @param jobj Java model object
     * @param fieldName the name of a field in {@code jobj}'s type
     * @return the {@link org.jsimpledb.kv.KVDatabase} key of the field in the specified object
     * @throws TypeNotInSchemaVersionException if the current schema version does not contain the object's type
     * @throws IllegalArgumentException if {@code jobj} does not contain the specified field
     * @throws IllegalArgumentException if {@code fieldName} is otherwise invalid
     * @throws IllegalArgumentException if either parameter is null
     */
    public byte[] getKey(JObject jobj, String fieldName) {
        if (jobj == null)
            throw new IllegalArgumentException("null jobj");
        final TypeToken<?> type = this.jdb.getJClass(jobj.getObjId()).typeToken;
        final ReferencePath refPath = this.jdb.parseReferencePath(type, fieldName, false);
        if (refPath.getReferenceFields().length > 0)
            throw new IllegalArgumentException("invalid field name `" + fieldName + "'");
        if (!refPath.targetType.getRawType().isInstance(jobj))
            throw new IllegalArgumentException("jobj is not an instance of " + refPath.targetType); // should never happen
        return this.tx.getKey(jobj.getObjId(), refPath.targetFieldInfo.storageId);
    }

// Snapshots

    /**
     * Get the default {@link SnapshotJTransaction} associated with this instance.
     *
     * <p>
     * The default {@link SnapshotJTransaction} uses {@link ValidationMode#MANUAL}.
     * </p>
     *
     * @see JObject#copyOut JObject.copyOut()
     */
    public synchronized SnapshotJTransaction getSnapshotTransaction() {
        if (this.snapshotTransaction == null)
            this.snapshotTransaction = new SnapshotJTransaction(this, ValidationMode.MANUAL);
        return this.snapshotTransaction;
    }

    /**
     * Copy the specified object into the specified destination transaction. If the target object already exists, it's
     * schema version will be updated to match the source object if necessary, otherwise it will be created.
     * {@link org.jsimpledb.annotation.OnVersionChange &#64;OnVersionChange},
     * {@link org.jsimpledb.annotation.OnCreate &#64;OnCreate} and
     * {@link org.jsimpledb.annotation.OnCreate &#64;OnChange} notifications will be delivered accordingly
     * (however, for create and change notifications in {@code dest}, these annotations must have
     * {@code snapshotTransactions = true} if {@code dest} is a {@link SnapshotJTransaction}).
     *
     * <p>
     * Circular references are handled properly: if an object is encountered more than once, it is not copied again.
     * The {@code seen} parameter tracks which objects have already been copied. For a "fresh" copy operation, pass a newly
     * created instance; for a copy operation that is a continuation of a previous copy, the {@code seen} may be reused.
     * </p>
     *
     * <p>
     * Does nothing if this instance and {@code dest} are the same instance and {@code srcId} and {@code dstId} are equal.
     * This instance and {@code dest} must be compatible in that for any schema versions encountered, those schema versions
     * must be identical in both transactions.
     * </p>
     *
     * <p>
     * Note: if two threads attempt to copy objects between the same two transactions at the same time but in opposite directions,
     * deadlock could result.
     * </p>
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#copyIn JObject.copyIn()},
     * {@link JObject#copyOut JObject.copyOut()}, or {@link JObject#copyTo JObject.copyTo()} would be used instead.
     * </p>
     *
     * @param dest destination transaction
     * @param srcObj source object
     * @param dstId target object ID, or null for the object ID of {@code srcObj}
     * @param seen tracks which indirectly referenced objects have already been copied
     * @param refPaths zero or more reference paths that refer to additional objects to be copied
     * @return the copied object, i.e., the object having ID {@code dstId} in {@code dest}
     * @throws DeletedObjectException if {@code srcObj} does not exist in this transaction
     * @throws org.jsimpledb.core.SchemaMismatchException if the schema corresponding to {@code srcObj}'s object's version
     *  is not identical in this instance and {@code dest} (as well for any referenced objects)
     * @throws TypeNotInSchemaVersionException if the current schema version does not contain the source object's type
     * @throws StaleTransactionException if this transaction or {@code dest} is no longer usable
     * @throws ReadOnlyTransactionException if {@code dest}'s underlying transaction
     *  is {@linkplain Transaction#setReadOnly set read-only}
     * @throws IllegalArgumentException if any path in {@code refPaths} is invalid
     * @throws IllegalArgumentException if any parameter is null
     * @see JObject#copyTo JObject.copyTo()
     * @see JObject#copyOut JObject.copyOut()
     * @see JObject#copyIn JObject.copyIn()
     * @see #copyTo(JTransaction, ObjIdSet, Iterable)
     */
    public JObject copyTo(JTransaction dest, JObject srcObj, ObjId dstId, ObjIdSet seen, String... refPaths) {

        // Sanity check
        if (dest == null)
            throw new IllegalArgumentException("null destination transaction");
        if (srcObj == null)
            throw new IllegalArgumentException("null srcObj");
        if (seen == null)
            throw new IllegalArgumentException("null seen");
        if (refPaths == null)
            throw new IllegalArgumentException("null refPaths");

        // Handle possible re-entrant object cache load
        srcObj.getTransaction().getJObjectCache().registerJObject(srcObj);

        // Get source and dest ID
        final ObjId srcId = srcObj.getObjId();
        if (dstId == null)
            dstId = srcId;

        // Check trivial case
        if (this.tx == dest.tx && srcId.equals(dstId))
            return dest.getJObject(dstId);

        // Parse paths
        final TypeToken<?> startType = this.jdb.getJClass(srcId).typeToken;
        final HashSet<ReferencePath> paths = new HashSet<>(refPaths.length);
        for (String refPath : refPaths) {

            // Parse reference path
            if (refPath == null)
                throw new IllegalArgumentException("null refPath");
            final ReferencePath path = this.jdb.parseReferencePath(startType, refPath, null);

            // Verify target field is a reference field; convert a complex target field into its reference sub-field(s)
            final String lastFieldName = refPath.substring(refPath.lastIndexOf('.') + 1);
            final JFieldInfo targetFieldInfo = this.jdb.jfieldInfos.get(path.getTargetField());
            if (targetFieldInfo instanceof JComplexFieldInfo) {
                final JComplexFieldInfo superFieldInfo = (JComplexFieldInfo)targetFieldInfo;
                boolean foundReferenceSubFieldInfo = false;
                for (JSimpleFieldInfo subFieldInfo : superFieldInfo.getSubFieldInfos()) {
                    if (subFieldInfo instanceof JReferenceFieldInfo) {
                        paths.add(this.jdb.parseReferencePath(startType,
                          refPath + "." + superFieldInfo.getSubFieldInfoName(subFieldInfo), true));
                        foundReferenceSubFieldInfo = true;
                    }
                }
                if (!foundReferenceSubFieldInfo) {
                    throw new IllegalArgumentException("the last field `" + lastFieldName
                      + "' of path `" + refPath + "' does not contain any reference sub-fields");
                }
            } else {
                if (!(targetFieldInfo instanceof JReferenceFieldInfo)) {
                    throw new IllegalArgumentException("the last field `" + lastFieldName
                      + "' of path `" + path + "' is not a reference field");
                }
                paths.add(path);
            }
        }

        // Ensure object is copied even when there are zero reference paths
        this.copyTo(seen, dest, srcId, dstId, true, new ArrayDeque<Integer>());

        // Recurse over each reference path
        for (ReferencePath path : paths) {
            final int[] storageIds = path.getReferenceFields();

            // Convert reference path, including final target field, into a list of JReferenceFields
            final ArrayDeque<Integer> fields = new ArrayDeque<>(storageIds.length + 1);
            fields.addAll(Ints.asList(storageIds));
            fields.add(path.getTargetField());

            // Recurse over this path
            this.copyTo(seen, dest, srcId, dstId, false/*doesn't matter*/, fields);
        }

        // Done
        return dest.getJObject(dstId);
    }

    /**
     * Copy the objects in the specified {@link Iterable} into the specified destination transaction.
     * If a target object already exists, it's schema version will be updated to match the source object if necessary,
     * otherwise it will be created.
     * {@link org.jsimpledb.annotation.OnVersionChange &#64;OnVersionChange},
     * {@link org.jsimpledb.annotation.OnCreate &#64;OnCreate} and
     * {@link org.jsimpledb.annotation.OnCreate &#64;OnChange} notifications will be delivered accordingly
     * (however, for create and change notifications in {@code dest}, these annotations must have
     * {@code snapshotTransactions = true} if {@code dest} is a {@link SnapshotJTransaction}).
     *
     * <p>
     * Circular references are handled properly: if an object is encountered more than once, it is not copied again.
     * The {@code seen} set tracks which objects have already been copied. For a "fresh" copy operation, pass a newly
     * created instance; for a copy operation that is a continuation of a previous copy, the {@code seen} may be reused.
     * </p>
     *
     * <p>
     * If an object is encountered more than once, it is not copied again.
     * Does nothing if this instance and {@code dest} are the same instance.
     * </p>
     *
     * <p>
     * Does nothing if this instance and {@code dest} are the same instance and {@code srcId} and {@code dstId} are equal.
     * This instance and {@code dest} must be compatible in that for any schema versions encountered, those schema versions
     * must be identical in both transactions.
     * </p>
     *
     * <p>
     * Note: if two threads attempt to copy objects between the same two transactions at the same time but in opposite directions,
     * deadlock could result.
     * </p>
     *
     * @param dest destination transaction
     * @param jobjs {@link Iterable} returning the objects to copy; null values are ignored
     * @param seen tracks which objects have already been copied
     * @throws DeletedObjectException if an object in {@code jobjs} does not exist in this transaction
     * @throws org.jsimpledb.core.SchemaMismatchException if the schema version corresponding to an object in
     *  {@code jobjs} is not identical in this instance and {@code dest}
     * @throws StaleTransactionException if this transaction or {@code dest} is no longer usable
     * @throws ReadOnlyTransactionException if {@code dest}'s underlying transaction
     *  is {@linkplain Transaction#setReadOnly set read-only}
     * @throws IllegalArgumentException if {@code dest} or {@code jobjs} is null
     * @see #copyTo(JTransaction, JObject, ObjId, ObjIdSet, String[])
     */
    public void copyTo(JTransaction dest, ObjIdSet seen, Iterable<? extends JObject> jobjs) {

        // Sanity check
        if (dest == null)
            throw new IllegalArgumentException("null dest");
        if (seen == null)
            throw new IllegalArgumentException("null seen");
        if (jobjs == null)
            throw new IllegalArgumentException("null jobjs");

        // Check trivial case
        if (this.tx == dest.tx)
            return;

        // Copy objects
        for (JObject jobj : jobjs) {

            // Get next object
            if (jobj == null)
                continue;

            // Handle possible re-entrant object cache load
            jobj.getTransaction().getJObjectCache().registerJObject(jobj);

            // Copy object
            final ObjId id = jobj.getObjId();
            this.copyTo(seen, dest, id, id, true, new ArrayDeque<Integer>());
        }
    }

    void copyTo(ObjIdSet seen, JTransaction dest, ObjId srcId, ObjId dstId, boolean required, Deque<Integer> fields) {

        // Already copied this object?
        if (!seen.add(dstId))
            return;

        // Copy current instance
        try {
            this.tx.copy(srcId, dstId, dest.tx);
        } catch (DeletedObjectException e) {
            if (required)
                throw e;
        }

        // Recurse through the next reference field in the path
        if (fields.isEmpty())
            return;
        final int storageId = fields.removeFirst();
        final JReferenceFieldInfo referenceFieldInfo = this.jdb.getJFieldInfo(storageId, JReferenceFieldInfo.class);
        final int parentStorageId = referenceFieldInfo.getParentStorageId();
        if (parentStorageId != 0) {
            final JComplexFieldInfo parentInfo = this.jdb.getJFieldInfo(parentStorageId, JComplexFieldInfo.class);
            parentInfo.copyRecurse(seen, this, dest, srcId, storageId, fields);
        } else {
            assert referenceFieldInfo instanceof JReferenceFieldInfo;
            final ObjId referrent = (ObjId)this.tx.readSimpleField(srcId, storageId, false);
            if (referrent != null)
                this.copyTo(seen, dest, referrent, referrent, false, fields);
        }
    }

// Object/Field Access

    /**
     * Get the Java model object that has the given ID and whose state derives from this transaction.
     * If this instance is a {@link SnapshotJTransaction}, the unique instance associated with this transaction
     * will be returned. Otherwise, the instance shared by all non-snapshot {@link JTransaction}s will be returned.
     *
     * <p>
     * It is guaranteed that for any particular {@code id}, the same Java instance will always be returned by this instance.
     * </p>
     *
     * <p>
     * A non-null object is always returned, but the corresponding object may not exist in this transaction.
     * In that case, attempts to access its fields will throw {@link org.jsimpledb.core.DeletedObjectException}.
     * </p>
     *
     * @param id object ID
     * @return Java model object
     * @throws IllegalArgumentException if {@code id} is null
     * @see #getJObject(ObjId, Class)
     * @see #getJObject(JObject)
     * @see JSimpleDB#getJObject JSimpleDB.getJObject()
     */
    public JObject getJObject(ObjId id) {
        return this.getJObjectCache().getJObject(id);
    }

    /**
     * Get the Java model object with the given object ID and whose state derives from this transaction, cast to the given type.
     * This method just invoke {@link #getJObject(ObjId)} and then casts the result.
     *
     * @param id object ID
     * @return Java model object
     * @throws ClassCastException if the Java model object does not have type {@code type}
     * @throws IllegalArgumentException if {@code id} or {@code type} is null
     * @see #getJObject(ObjId)
     * @see #getJObject(JObject)
     */
    public <T> T getJObject(ObjId id, Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("null type");
        return type.cast(this.getJObject(id));
    }

    /**
     * Get the Java model object with the same object ID as the given object and whose state derives from this transaction.
     * This method is equivalent to {@code getJObject(jobj.getObjId())} followed by an appropriate cast to type {@code T}.
     *
     * @param jobj Java model object
     * @return Java model object in this transaction with the same object ID (possibly {@code jobj} itself)
     * @throws IllegalArgumentException if {@code jobj} is null, or not a {@link JSimpleDB} database object
     * @throws ClassCastException if the Java model object in this transaction somehow does not have the same type as {@code jobj}
     * @see #getJObject(ObjId)
     * @see #getJObject(ObjId, Class)
     */
    @SuppressWarnings("unchecked")
    public <T extends JObject> T getJObject(T jobj) {
        final Class<?> modelClass = JSimpleDB.getModelClass(jobj);
        if (modelClass == null)
            throw new IllegalArgumentException("can't determine model class for type " + jobj.getClass().getName());
        return (T)modelClass.cast(this.getJObject(jobj.getObjId()));
    }

    /**
     * Create a new instance of the given model class in this transaction.
     *
     * @param type an annotated Java object model type
     * @return newly created instance
     * @throws IllegalArgumentException if {@code type} is not a known Java object model type
     * @throws ReadOnlyTransactionException if the underlying transaction is {@linkplain Transaction#setReadOnly set read-only}
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    public <T> T create(Class<T> type) {
        return this.create(this.jdb.getJClass(TypeToken.of(type)));
    }

    /**
     * Create a new instance of the given type in this transaction.
     *
     * @param jclass object type
     * @return newly created instance
     * @throws IllegalArgumentException if {@code jclass} is not valid for this instance
     * @throws ReadOnlyTransactionException if the underlying transaction is {@linkplain Transaction#setReadOnly set read-only}
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings("unchecked")
    public <T> T create(JClass<T> jclass) {
        final ObjId id = this.tx.create(jclass.storageId);
        return (T)jclass.getTypeToken().getRawType().cast(this.getJObject(id));
    }

    /**
     * Delete the object with the given object ID in this transaction.
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#delete} would be used instead.
     * </p>
     *
     * @param jobj the object to delete
     * @return true if object was found and deleted, false if object was not found
     * @throws ReferencedObjectException if the object is referenced by some other object
     *  through a reference field configured for {@link org.jsimpledb.core.DeleteAction#EXCEPTION}
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws ReadOnlyTransactionException if the underlying transaction is {@linkplain Transaction#setReadOnly set read-only}
     * @throws NullPointerException if {@code jobj} is null
     */
    public boolean delete(JObject jobj) {
        jobj.getTransaction().getJObjectCache().registerJObject(jobj);              // handle possible re-entrant object cache load
        return this.tx.delete(jobj.getObjId());
    }

    /**
     * Determine whether the object with the given object ID exists in this transaction.
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#exists} would be used instead.
     * </p>
     *
     * @param jobj the object to test for existence
     * @return true if object was found, false if object was not found
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws NullPointerException if {@code jobj} is null
     */
    public boolean exists(JObject jobj) {
        return this.tx.exists(jobj.getObjId());
    }

    /**
     * Recreate the given instance in this transaction.
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#recreate} would be used instead.
     * </p>
     *
     * @param jobj the object to recreate
     * @return true if the object was recreated, false if the object already existed
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws ReadOnlyTransactionException if the underlying transaction is {@linkplain Transaction#setReadOnly set read-only}
     * @throws NullPointerException if {@code jobj} is null
     */
    public boolean recreate(JObject jobj) {
        jobj.getTransaction().getJObjectCache().registerJObject(jobj);              // handle possible re-entrant object cache load
        return this.tx.create(jobj.getObjId());
    }

    /**
     * Add the given instance to the validation queue for validation, which will occur either at {@link #commit} time
     * or at the next invocation of {@link #validate}, whichever occurs first.
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#revalidate} would be used instead.
     * </p>
     *
     * @param jobj the object to revalidate
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws IllegalStateException if transaction commit is already in progress
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws NullPointerException if {@code jobj} is null
     */
    public void revalidate(JObject jobj) {
        final ObjId id = jobj.getObjId();
        if (!this.tx.exists(id))
            throw new DeletedObjectException(id);
        this.revalidate(Collections.singleton(id));
    }

    /**
     * Clear the validation queue associated with this transaction. Any previously enqueued objects that have not
     * yet been validated will no longer receive validation.
     *
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws IllegalStateException if transaction commit is already in progress
     */
    public synchronized void resetValidationQueue() {
        if (!this.tx.isValid())
            throw new StaleTransactionException(this.tx);
        if (this.committing)
            throw new IllegalStateException("commit() has already been invoked");
        this.validationQueue.clear();
    }

    void revalidate(Collection<? extends ObjId> ids) {
        if (!this.tx.isValid())
            throw new StaleTransactionException(this.tx);
        if (this.validationMode == ValidationMode.DISABLED)
            return;
        synchronized (this) {
            if (this.committing)
                throw new IllegalStateException("commit() has already been invoked");
            this.validationQueue.addAll(ids);
        }
    }

    /**
     * Get this schema version of the specified object. Does not change the object's schema version.
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#getSchemaVersion} would be used instead.
     * </p>
     *
     * @param jobj object whose version to query
     * @return object's schema version
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws NullPointerException if {@code jobj} is null
     */
    public int getSchemaVersion(JObject jobj) {
        return this.tx.getSchemaVersion(jobj.getObjId());
    }

    /**
     * Update the schema version of the specified object, if necessary, so that its version matches
     * the schema version associated with this instance's {@link JSimpleDB}.
     *
     * <p>
     * If a version change occurs, matching {@link org.jsimpledb.annotation.OnVersionChange &#64;OnVersionChange}
     * methods will be invoked prior to this method returning.
     * </p>
     *
     * <p>
     * This method is typically only used by generated classes; normally, {@link JObject#upgrade} would be used instead.
     * </p>
     *
     * @param jobj object to update
     * @return true if the object's schema version was changed, false if it was already updated
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws TypeNotInSchemaVersionException if the current schema version does not contain the object's type
     * @throws ReadOnlyTransactionException if the underlying transaction is {@linkplain Transaction#setReadOnly set read-only}
     * @throws NullPointerException if {@code jobj} is null
     */
    public boolean updateSchemaVersion(JObject jobj) {
        jobj.getTransaction().getJObjectCache().registerJObject(jobj);              // handle possible re-entrant object cache load
        return this.tx.updateSchemaVersion(jobj.getObjId());
    }

    /**
     * Read a simple field. This returns the value returned by {@link Transaction#readSimpleField Transaction.readSimpleField()}
     * with {@link ObjId}s converted into {@link JObject}s, etc.
     *
     * <p>
     * This method is used by generated {@link org.jsimpledb.annotation.JField &#64;JField} getter override methods
     * and not normally invoked directly by user code.
     * </p>
     *
     * @param jobj object containing the field
     * @param storageId storage ID of the {@link JSimpleField}
     * @param updateVersion true to first automatically update the object's schema version, false to not change it
     * @return value of the field in the object
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws UnknownFieldException if no {@link JSimpleField} corresponding to {@code storageId} exists
     * @throws TypeNotInSchemaVersionException if {@code updateVersion} is true but {@code jobj} has a type
     *  that does not exist in this instance's schema version
     * @throws NullPointerException if {@code jobj} is null
     */
    public Object readSimpleField(JObject jobj, int storageId, boolean updateVersion) {
        return this.convert(this.jdb.getJFieldInfo(storageId, JSimpleFieldInfo.class).getConverter(this),
          this.tx.readSimpleField(jobj.getObjId(), storageId, updateVersion));
    }

    /**
     * Write a simple field. This writes the value via {@link Transaction#writeSimpleField Transaction.writeSimpleField()}
     * after converting {@link JObject}s into {@link ObjId}s, etc.
     *
     * <p>
     * This method is used by generated {@link org.jsimpledb.annotation.JField &#64;JField} setter override methods
     * and not normally invoked directly by user code.
     * </p>
     *
     * @param jobj object containing the field
     * @param storageId storage ID of the {@link JSimpleField}
     * @param value new value for the field
     * @param updateVersion true to first automatically update the object's schema version, false to not change it
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws ReadOnlyTransactionException if the underlying transaction is {@linkplain Transaction#setReadOnly set read-only}
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws UnknownFieldException if no {@link JSimpleField} corresponding to {@code storageId} exists
     * @throws TypeNotInSchemaVersionException if {@code updateVersion} is true but {@code jobj} has a type
     *  that does not exist in this instance's schema version
     * @throws IllegalArgumentException if {@code value} is not an appropriate value for the field
     * @throws NullPointerException if {@code jobj} is null
     */
    public void writeSimpleField(JObject jobj, int storageId, Object value, boolean updateVersion) {
        jobj.getTransaction().getJObjectCache().registerJObject(jobj);              // handle possible re-entrant object cache load
        final Converter<?, ?> converter = this.jdb.getJFieldInfo(storageId, JSimpleFieldInfo.class).getConverter(this);
        if (converter != null)
            value = this.convert(converter.reverse(), value);
        this.tx.writeSimpleField(jobj.getObjId(), storageId, value, updateVersion);
    }

    /**
     * Read a counter field.
     *
     * <p>
     * This method is used by generated {@link org.jsimpledb.annotation.JField &#64;JField} getter override methods
     * and not normally invoked directly by user code.
     * </p>
     *
     * @param jobj object containing the field
     * @param storageId storage ID of the {@link JCounterField}
     * @param updateVersion true to first automatically update the object's schema version, false to not change it
     * @return value of the counter in the object
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws UnknownFieldException if no {@link JCounterField} corresponding to {@code storageId} exists
     * @throws TypeNotInSchemaVersionException if {@code updateVersion} is true but {@code jobj} has a type
     *  that does not exist in this instance's schema version
     * @throws NullPointerException if {@code jobj} is null
     */
    public Counter readCounterField(JObject jobj, int storageId, boolean updateVersion) {
        this.jdb.getJFieldInfo(storageId, JCounterFieldInfo.class);         // validate field type
        if (updateVersion)
            this.tx.updateSchemaVersion(jobj.getObjId());
        return new Counter(this.tx, jobj.getObjId(), storageId, updateVersion);
    }

    /**
     * Read a set field. This returns the set returned by {@link Transaction#readSetField Transaction.readSetField()} with
     * {@link ObjId}s converted into {@link JObject}s, etc.
     *
     * <p>
     * This method is used by generated {@link org.jsimpledb.annotation.JSetField &#64;JSetField}
     * getter override methods and not normally invoked directly by user code.
     * </p>
     *
     * @param jobj object containing the field
     * @param storageId storage ID of the {@link JSetField}
     * @param updateVersion true to first automatically update the object's schema version, false to not change it
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws UnknownFieldException if no {@link JSetField} corresponding to {@code storageId} exists
     * @throws TypeNotInSchemaVersionException if {@code updateVersion} is true but {@code jobj} has a type
     *  that does not exist in this instance's schema version
     * @throws NullPointerException if {@code jobj} is null
     */
    public NavigableSet<?> readSetField(JObject jobj, int storageId, boolean updateVersion) {
        return this.convert(this.jdb.getJFieldInfo(storageId, JSetFieldInfo.class).getConverter(this),
          this.tx.readSetField(jobj.getObjId(), storageId, updateVersion));
    }

    /**
     * Read a list field. This returns the list returned by {@link Transaction#readListField Transaction.readListField()} with
     * {@link ObjId}s converted into {@link JObject}s, etc.
     *
     * <p>
     * This method is used by generated {@link org.jsimpledb.annotation.JListField &#64;JListField}
     * getter override methods and not normally invoked directly by user code.
     * </p>
     *
     * @param jobj object containing the field
     * @param storageId storage ID of the {@link JListField}
     * @param updateVersion true to first automatically update the object's schema version, false to not change it
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws UnknownFieldException if no {@link JListField} corresponding to {@code storageId} exists
     * @throws TypeNotInSchemaVersionException if {@code updateVersion} is true but {@code jobj} has a type
     *  that does not exist in this instance's schema version
     * @throws NullPointerException if {@code jobj} is null
     */
    public List<?> readListField(JObject jobj, int storageId, boolean updateVersion) {
        return this.convert(this.jdb.getJFieldInfo(storageId, JListFieldInfo.class).getConverter(this),
          this.tx.readListField(jobj.getObjId(), storageId, updateVersion));
    }

    /**
     * Read a map field. This returns the map returned by {@link Transaction#readMapField Transaction.readMapField()} with
     * {@link ObjId}s converted into {@link JObject}s, etc.
     *
     * <p>
     * This method is used by generated {@link org.jsimpledb.annotation.JMapField &#64;JMapField}
     * getter override methods and not normally invoked directly by user code.
     * </p>
     *
     * @param jobj object containing the field
     * @param storageId storage ID of the {@link JMapField}
     * @param updateVersion true to first automatically update the object's schema version, false to not change it
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws DeletedObjectException if {@code jobj} does not exist in this transaction
     * @throws UnknownFieldException if no {@link JMapField} corresponding to {@code storageId} exists
     * @throws TypeNotInSchemaVersionException if {@code updateVersion} is true but {@code jobj} has a type
     *  that does not exist in this instance's schema version
     * @throws NullPointerException if {@code jobj} is null
     */
    public NavigableMap<?, ?> readMapField(JObject jobj, int storageId, boolean updateVersion) {
        return this.convert(this.jdb.getJFieldInfo(storageId, JMapFieldInfo.class).getConverter(this),
          this.tx.readMapField(jobj.getObjId(), storageId, updateVersion));
    }

// Reference Path Access

    /**
     * Find all objects that refer to any object in the given target set through the specified path of references.
     *
     * @param startType starting Java type for the path
     * @param path dot-separated path of one or more reference fields
     * @param targetObjects target objects
     * @return set of objects that refer to any of the {@code targetObjects} via the {@code path} from {@code startType}
     * @throws UnknownFieldException if {@code path} contains an unknown field
     * @throws IllegalArgumentException if {@code path} is invalid, e.g., does not end on a reference field
     * @throws IllegalArgumentException if any parameter is null
     */
    public <T> NavigableSet<T> invertReferencePath(Class<T> startType, String path, Iterable<? extends JObject> targetObjects) {
        if (targetObjects == null)
            throw new IllegalArgumentException("null targetObjects");
        final ReferencePath refPath = this.jdb.parseReferencePath(TypeToken.of(startType), path, true);
        final int targetField = refPath.getTargetField();
        try {
            this.jdb.getJFieldInfo(targetField, JReferenceFieldInfo.class);
        } catch (UnknownFieldException e) {
            final String fieldName = path.substring(path.lastIndexOf('.') + 1);
            throw new IllegalArgumentException("last field `" + fieldName + "' of path `" + path + "' is not a reference field", e);
        }
        final int[] refs = Ints.concat(refPath.getReferenceFields(), new int[] { targetField });
        final NavigableSet<ObjId> ids = this.tx.invertReferencePath(refs,
          Iterables.transform(targetObjects, new ReferenceConverter<JObject>(this, JObject.class)));
        return new ConvertedNavigableSet<T, ObjId>(ids, new ReferenceConverter<T>(this, startType));
    }

// Index Access

    /**
     * Get the index on a simple field. The simple field may be a sub-field of a complex field.
     *
     * @param targetType Java type containing the indexed field; may also be any super-type (e.g., an interface type),
     *  as long as {@code fieldName} is not ambiguous among all sub-types
     * @param fieldName name of the indexed field; for complex fields,
     *  must include the sub-field name (e.g., {@code "mylist.element"}, {@code "mymap.key"})
     * @param valueType the Java type corresponding to the field value
     * @return read-only, real-time view of field values mapped to sets of objects having that value in the field
     * @throws IllegalArgumentException if any parameter is null, or invalid
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <V, T> Index<V, T> queryIndex(Class<T> targetType, String fieldName, Class<V> valueType) {
        final IndexInfo info = this.getIndexInfo(new IndexInfoKey(fieldName, false, targetType, valueType));
        final CoreIndex<?, ObjId> index = info.applyFilters(this.tx.queryIndex(info.fieldInfo.storageId));
        final Converter<?, ?> valueConverter = this.getReverseConverter(info.fieldInfo);
        final Converter<T, ObjId> targetConverter = new ReferenceConverter<T>(this, targetType);
        return new ConvertedIndex(index, valueConverter, targetConverter);
    }

    /**
     * Get the composite index on a list field that includes list indicies.
     *
     * @param targetType type containing the indexed field; may also be any super-type (e.g., an interface type),
     *  as long as {@code fieldName} is not ambiguous among all sub-types
     * @param fieldName name of the indexed field; must include {@code "element"} sub-field name (e.g., {@code "mylist.element"})
     * @param valueType the Java type corresponding to list elements
     * @return read-only, real-time view of field values, objects having that value in the field, and corresponding list indicies
     * @throws IllegalArgumentException if any parameter is null, or invalid
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <V, T> Index2<V, T, Integer> queryListElementIndex(Class<T> targetType, String fieldName, Class<V> valueType) {
        final IndexInfo info = this.getIndexInfo(new IndexInfoKey(fieldName, false, targetType, valueType));
        if (!(info.superFieldInfo instanceof JListFieldInfo))
            throw new IllegalArgumentException("`" + fieldName + "' is not a list element sub-field");
        final CoreIndex2<?, ObjId, Integer> index = info.applyFilters(this.tx.queryListElementIndex(info.superFieldInfo.storageId));
        final Converter<?, ?> valueConverter = this.getReverseConverter(info.fieldInfo);
        final Converter<T, ObjId> targetConverter = new ReferenceConverter<T>(this, targetType);
        return new ConvertedIndex2(index, valueConverter, targetConverter, Converter.<Integer>identity());
    }

    /**
     * Get the composite index on a map value field that includes map keys.
     *
     * @param targetType type containing the indexed field; may also be any super-type (e.g., an interface type),
     *  as long as {@code fieldName} is not ambiguous among all sub-types
     * @param fieldName name of the indexed field; must include {@code "value"} sub-field name (e.g., {@code "mymap.value"})
     * @param valueType the Java type corresponding to list elements
     * @return read-only, real-time view of map values, objects having that value in the map field, and corresponding map keys
     * @throws IllegalArgumentException if any parameter is null, or invalid
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <V, T, K> Index2<V, T, K> queryMapValueIndex(Class<T> targetType,
      String fieldName, Class<V> valueType, Class<K> keyType) {
        final IndexInfo info = this.getIndexInfo(new IndexInfoKey(fieldName, false, targetType, valueType, keyType));
        if (!(info.superFieldInfo instanceof JMapFieldInfo))
            throw new IllegalArgumentException("`" + fieldName + "' is not a map value sub-field");
        final JMapFieldInfo mapFieldInfo = (JMapFieldInfo)info.superFieldInfo;
        if (!info.fieldInfo.equals(mapFieldInfo.getValueFieldInfo()))
            throw new IllegalArgumentException("`" + fieldName + "' is not a map value sub-field");
        final CoreIndex2<?, ObjId, ?> index = info.applyFilters(this.tx.queryMapValueIndex(mapFieldInfo.storageId));
        final Converter<?, ?> valueConverter = this.getReverseConverter(info.fieldInfo);
        final Converter<?, ?> keyConverter = this.getReverseConverter(mapFieldInfo.getKeyFieldInfo());
        final Converter<T, ObjId> targetConverter = new ReferenceConverter<T>(this, targetType);
        return new ConvertedIndex2(index, valueConverter, targetConverter, keyConverter);
    }

    /**
     * Access a composite index on two fields.
     *
     * @param targetType type containing the indexed fields; may also be any super-type (e.g., an interface type)
     * @param indexName the name of the composite index
     * @param value1Type the Java type corresponding to the first field value
     * @param value2Type the Java type corresponding to the second field value
     * @return read-only, real-time view of the fields' values and the objects having those values in the fields
     * @throws IllegalArgumentException if any parameter is null, or invalid
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <V1, V2, T> Index2<V1, V2, T> queryCompositeIndex(Class<T> targetType,
      String indexName, Class<V1> value1Type, Class<V2> value2Type) {
        final IndexInfo info = this.getIndexInfo(new IndexInfoKey(indexName, true, targetType, value1Type, value2Type));
        final CoreIndex2<?, ?, ObjId> index = info.applyFilters(this.tx.queryCompositeIndex2(info.indexInfo.storageId));
        final Converter<?, ?> value1Converter = this.getReverseConverter(info.indexInfo.jfieldInfos.get(0));
        final Converter<?, ?> value2Converter = this.getReverseConverter(info.indexInfo.jfieldInfos.get(1));
        final Converter<T, ObjId> targetConverter = new ReferenceConverter<T>(this, targetType);
        return new ConvertedIndex2(index, value1Converter, value2Converter, targetConverter);
    }

    /**
     * Access a composite index on three fields.
     *
     * @param targetType type containing the indexed fields; may also be any super-type (e.g., an interface type)
     * @param indexName the name of the composite index
     * @param value1Type the Java type corresponding to the first field value
     * @param value2Type the Java type corresponding to the second field value
     * @param value3Type the Java type corresponding to the third field value
     * @return read-only, real-time view of the fields' values and the objects having those values in the fields
     * @throws IllegalArgumentException if any parameter is null, or invalid
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <V1, V2, V3, T> Index3<V1, V2, V3, T> queryCompositeIndex(Class<T> targetType,
      String indexName, Class<V1> value1Type, Class<V2> value2Type, Class<V3> value3Type) {
        final IndexInfo info = this.getIndexInfo(new IndexInfoKey(indexName, true, targetType, value1Type, value2Type, value3Type));
        final CoreIndex3<?, ?, ?, ObjId> index = info.applyFilters(this.tx.queryCompositeIndex3(info.indexInfo.storageId));
        final Converter<?, ?> value1Converter = this.getReverseConverter(info.indexInfo.jfieldInfos.get(0));
        final Converter<?, ?> value2Converter = this.getReverseConverter(info.indexInfo.jfieldInfos.get(1));
        final Converter<?, ?> value3Converter = this.getReverseConverter(info.indexInfo.jfieldInfos.get(2));
        final Converter<T, ObjId> targetConverter = new ReferenceConverter<T>(this, targetType);
        return new ConvertedIndex3(index, value1Converter, value2Converter, value3Converter, targetConverter);
    }

    /**
     * Query an index by storage ID. For storage ID's corresponding to simple fields, this method returns an
     * {@link Index}, except for list element and map value fields, for which an {@link Index2} is returned.
     * For storage ID's corresponding to composite indexes, this method returns an {@link Index2}, {@link Index3},
     * etc. as appropriate.
     *
     * <p>
     * This method exists mainly for the convenience of programmatic tools, etc.
     * </p>
     *
     * @param storageId indexed {@link JSimpleField}'s storage ID
     * @return read-only, real-time view of the fields' values and the objects having those values in the fields
     * @throws IllegalArgumentException if {@code storageId} does not correspond to an indexed field or composite index
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object queryIndex(int storageId) {

        // Look for a composite index
        final JCompositeIndexInfo indexInfo = this.jdb.jcompositeIndexInfos.get(storageId);
        if (indexInfo != null) {
            final Converter<JObject, ObjId> targetConverter = new ReferenceConverter<JObject>(this, JObject.class);
            switch (indexInfo.jfieldInfos.size()) {
            case 2:
            {
                final Converter<?, ?> value1Converter = this.getReverseConverter(indexInfo.jfieldInfos.get(0));
                final Converter<?, ?> value2Converter = this.getReverseConverter(indexInfo.jfieldInfos.get(1));
                return new ConvertedIndex2(this.tx.queryCompositeIndex2(indexInfo.storageId),
                  value1Converter, value2Converter, targetConverter);
            }
            case 3:
            {
                final Converter<?, ?> value1Converter = this.getReverseConverter(indexInfo.jfieldInfos.get(0));
                final Converter<?, ?> value2Converter = this.getReverseConverter(indexInfo.jfieldInfos.get(1));
                final Converter<?, ?> value3Converter = this.getReverseConverter(indexInfo.jfieldInfos.get(2));
                return new ConvertedIndex3(this.tx.queryCompositeIndex3(indexInfo.storageId),
                  value1Converter, value2Converter, value3Converter, targetConverter);
            }
            // COMPOSITE-INDEX
            default:
                throw new RuntimeException("internal error");
            }
        }

        // Must be an indexed field
        final JFieldInfo someFieldInfo = this.jdb.jfieldInfos.get(storageId);
        if (someFieldInfo == null)
            throw new IllegalArgumentException("no composite index or simple indexed field exists with storage ID " + storageId);
        if (!(someFieldInfo instanceof JSimpleFieldInfo) || !((JSimpleFieldInfo)someFieldInfo).isIndexed()) {
            throw new IllegalArgumentException("storage ID " + storageId + " does not correspond to an indexed simple field (found "
              + someFieldInfo + " instead)");
        }

        // Build the appropriate index for the field
        final JSimpleFieldInfo fieldInfo = (JSimpleFieldInfo)someFieldInfo;
        final Converter<?, ?> valueConverter = this.getReverseConverter(fieldInfo);
        final ReferenceConverter<JObject> referenceConverter = new ReferenceConverter<JObject>(this, JObject.class);
        final int parentStorageId = fieldInfo.getParentStorageId();
        final JComplexFieldInfo parentInfo = parentStorageId != 0 ?
          this.jdb.getJFieldInfo(parentStorageId, JComplexFieldInfo.class) : null;
        if (parentInfo instanceof JListFieldInfo) {
            return new ConvertedIndex2(this.tx.queryListElementIndex(fieldInfo.storageId),
              valueConverter, referenceConverter, Converter.identity());
        } else if (parentInfo instanceof JMapFieldInfo
          && ((JMapFieldInfo)parentInfo).getSubFieldInfoName(fieldInfo).equals(MapField.VALUE_FIELD_NAME)) {
            final JMapFieldInfo mapFieldInfo = (JMapFieldInfo)parentInfo;
            final JSimpleFieldInfo keyFieldInfo = mapFieldInfo.getKeyFieldInfo();
            final Converter<?, ?> keyConverter = this.getReverseConverter(keyFieldInfo);
            return new ConvertedIndex2(this.tx.queryMapValueIndex(fieldInfo.storageId),
              valueConverter, referenceConverter, keyConverter);
        } else
            return new ConvertedIndex(this.tx.queryIndex(fieldInfo.storageId), valueConverter, referenceConverter);
    }

    private IndexInfo getIndexInfo(IndexInfoKey key) {
        try {
            return this.indexInfoCache.getUnchecked(key);
        } catch (UncheckedExecutionException e) {
            final Throwable t = e.getCause();
            if (t instanceof RuntimeException)
                throw (RuntimeException)t;
            if (t instanceof Error)
                throw (Error)t;
            throw e;
        }
    }

    private Converter<?, ?> getReverseConverter(JSimpleFieldInfo fieldInfo) {
        final Converter<?, ?> converter = fieldInfo.getConverter(this);
        return converter != null ? converter.reverse() : Converter.identity();
    }

// IndexInfoKey

    private static class IndexInfoKey {

        private final Class<?>[] types;
        private final boolean composite;
        private final String name;

        public IndexInfoKey(String name, boolean composite, Class<?>... types) {
            this.name = name;
            this.composite = composite;
            this.types = types;
        }

        public IndexInfo getIndexInfo(JSimpleDB jdb) {

            // Handle composite index
            if (this.composite)
                return new IndexInfo(jdb, this.types[0], this.name, Arrays.copyOfRange(this.types, 1, this.types.length));

            // Handle map value index
            if (this.types.length == 3)
                return new IndexInfo(jdb, this.types[0], this.name, this.types[1], this.types[2]);

            // Handle all others
            return new IndexInfo(jdb, this.types[0], this.name, this.types[1]);
        }

    // Object

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final IndexInfoKey that = (IndexInfoKey)obj;
            return this.name.equals(that.name)
              && this.composite == that.composite
              && this.types.equals(that.types);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode()
              ^ (this.composite ? 1 : 0)
              ^ this.types.hashCode();
        }
    }

// Transaction Lifecycle

    /**
     * Commit this transaction.
     *
     * <p>
     * Prior to actual commit, if this transaction was created with a validation mode other than {@link ValidationMode#DISABLED},
     * {@linkplain #validate validation} of outstanding objects in the validation queue is performed.
     * </p>
     *
     * <p>
     * If a {@link ValidationException} is thrown, the transaction is no longer usable. To perform validation and leave
     * the transaction open, invoke {@link #validate} prior to commit.
     * </p>
     *
     * @throws StaleTransactionException if this transaction is no longer usable
     * @throws org.jsimpledb.kv.RetryTransactionException from {@link org.jsimpledb.kv.KVTransaction#commit KVTransaction.commit()}
     * @throws ValidationException if a validation error is detected
     * @throws IllegalStateException if this method is invoked re-entrantly from within a validation check
     */
    public synchronized void commit() {

        // Sanity check
        if (!this.tx.isValid())
            throw new StaleTransactionException(this.tx);
        synchronized (this) {
            if (this.committing)
                throw new IllegalStateException("commit() invoked re-entrantly");
            this.committing = true;
        }

        // Do validation
        try {
            this.validate();
        } catch (ValidationException e) {
            this.tx.rollback();
            throw e;
        }

        // Commit
        this.tx.commit();
    }

    /**
     * Roll back this transaction.
     *
     * @throws StaleTransactionException if this transaction is no longer usable
     */
    public void rollback() {
        this.tx.rollback();
    }

    /**
     * Determine whether this transaction is still usable.
     *
     * @see Transaction#isValid
     */
    public boolean isValid() {
        return this.tx.isValid();
    }

    /**
     * Perform validation checks on all objects currently in the validation queue.
     * This method may be called at any time prior to {@link #commit} to
     * process and clear the queue of validatable objects.
     *
     * <p>
     * If validation fails, validation stops, all remaining unvalidated objects are left on the validation queue,
     * and a {@link ValidationException} is thrown. The transaction will remain usable.
     * </p>
     *
     * <p>
     * <b>Note:</b> if the this transaction was created with {@link ValidationMode#DISABLED}, then this method does nothing.
     * </p>
     *
     * @throws org.jsimpledb.kv.RetryTransactionException from {@link org.jsimpledb.kv.KVTransaction#commit KVTransaction.commit()}
     * @throws ValidationException if a validation error is detected
     * @throws IllegalStateException if transaction commit is already in progress
     * @throws StaleTransactionException if this transaction is no longer usable
     *
     * @see JObject#revalidate
     */
    public void validate() {

        // Sanity check
        if (!this.tx.isValid())
            throw new StaleTransactionException(this.tx);

        // Check validation mode
        if (this.validationMode == ValidationMode.DISABLED)
            return;

        // Do validation
        this.performAction(new Runnable() {
            @Override
            public void run() {
                JTransaction.this.doValidate();
            }
        });
    }

    /**
     * Invoke the given {@link Runnable} with this instance as the {@linkplain #getCurrent current transaction}.
     *
     * <p>
     * If another instance is currently associated with the current thread, it is set aside for the duration of
     * {@code action}'s execution, and then restored when {@code action} is complete.
     * </p>
     *
     * @param action action to perform
     * @throws IllegalArgumentException if {@code action} is null
     */
    public void performAction(Runnable action) {
        if (action == null)
            throw new IllegalArgumentException("null action");
        final JTransaction previous = CURRENT.get();
        CURRENT.set(this);
        try {
            action.run();
        } finally {
            CURRENT.set(previous);
        }
    }

// Internal methods

    private void doValidate() {
        while (true) {

            // Get next object
            final ObjId id;
            synchronized (this) {
                final Iterator<ObjId> i = this.validationQueue.iterator();
                try {
                    id = i.next();
                } catch (NoSuchElementException e) {
                    return;
                }
                i.remove();
            }

            // Does it still exist?
            if (!this.tx.exists(id))
                continue;

            // Do JSR 303 validation
            final JObject jobj = this.getJObject(id);
            final Set<ConstraintViolation<JObject>> violations = ValidationUtil.validate(jobj);
            if (!violations.isEmpty()) {
                throw new ValidationException(jobj, violations, "validation error for object " + id + " of type `"
                  + this.jdb.jclasses.get(id.getStorageId()).name + "':\n" + ValidationUtil.describe(violations));
            }

            // Do @Validate validation
            final JClass<?> jclass = this.jdb.getJClass(id);
            for (ValidateScanner<?>.MethodInfo info : jclass.validateMethods)
                Util.invoke(info.getMethod(), jobj);
        }
    }

// Object Cache

    JObjectCache getJObjectCache() {
        return this.jdb.jobjectCache;
    }

// InternalCreateListener

    private class InternalCreateListener implements CreateListener {

        @Override
        public void onCreate(Transaction tx, ObjId id) {
            final JClass<?> jclass;
            try {
                jclass = JTransaction.this.jdb.getJClass(id);
            } catch (TypeNotInSchemaVersionException e) {
                return;                                             // object type does not exist in our schema
            }
            this.doOnCreate(jclass, id);
        }

        // This method exists solely to bind the generic type parameters
        private <T> void doOnCreate(JClass<T> jclass, ObjId id) {
            Object jobj = null;
            for (OnCreateScanner<T>.MethodInfo info : jclass.onCreateMethods) {
                if (JTransaction.this instanceof SnapshotJTransaction && !info.getAnnotation().snapshotTransactions())
                    continue;
                if (jobj == null)
                    jobj = JTransaction.this.getJObject(id);
                Util.invoke(info.getMethod(), jobj);
            }
            if (validationMode == ValidationMode.AUTOMATIC)
                JTransaction.this.revalidate(Collections.singleton(id));
        }
    }

// InternalDeleteListener

    private class InternalDeleteListener implements DeleteListener {

        @Override
        public void onDelete(Transaction tx, ObjId id) {
            final JClass<?> jclass;
            try {
                jclass = JTransaction.this.jdb.getJClass(id);
            } catch (TypeNotInSchemaVersionException e) {
                return;                                             // object type does not exist in our schema
            }
            this.doOnDelete(jclass, id);
        }

        // This method exists solely to bind the generic type parameters
        private <T> void doOnDelete(JClass<T> jclass, ObjId id) {
            Object jobj = null;
            for (OnDeleteScanner<T>.MethodInfo info : jclass.onDeleteMethods) {
                if (JTransaction.this instanceof SnapshotJTransaction && !info.getAnnotation().snapshotTransactions())
                    continue;
                if (jobj == null)
                    jobj = JTransaction.this.getJObject(id);
                Util.invoke(info.getMethod(), jobj);
            }
        }
    }

// InternalVersionChangeListener

    private class InternalVersionChangeListener implements VersionChangeListener {

        @Override
        public void onVersionChange(Transaction tx, ObjId id, int oldVersion, int newVersion, Map<Integer, Object> oldFieldValues) {
            final JClass<?> jclass;
            try {
                jclass = JTransaction.this.jdb.getJClass(id);
            } catch (TypeNotInSchemaVersionException e) {
                return;                                             // object type does not exist in our schema
            }
            this.doOnVersionChange(jclass, id, oldVersion, newVersion, oldFieldValues);
        }

        // This method exists solely to bind the generic type parameters
        private <T> void doOnVersionChange(JClass<T> jclass, ObjId id,
          int oldVersion, int newVersion, Map<Integer, Object> oldFieldValues) {

            // Get old object type info
            final SchemaVersion oldSchema = JTransaction.this.tx.getSchema().getVersion(oldVersion);
            final ObjType objType = oldSchema.getObjType(id.getStorageId());

            // The object that was upgraded
            JObject jobj = null;

            // Convert old field values from core API objects to JDB layer objects
            final Map<Integer, Object> oldValuesByStorageId = Maps.transformEntries(oldFieldValues,
              new Maps.EntryTransformer<Integer, Object, Object>() {
                @Override
                public Object transformEntry(Integer storageId, Object oldValue) {
                    return JTransaction.this.convertCoreValue(objType.getField(storageId), oldValue);
                }
            });

            // Build alternate version of old values map that is keyed by field name instead of storage ID
            final Map<String, Object> oldValuesByName = Maps.transformValues(objType.getFieldsByName(),
              new Function<Field<?>, Object>() {
                @Override
                public Object apply(Field<?> field) {
                    return oldValuesByStorageId.get(field.getStorageId());
                }
            });

            // Invoke listener methods
            for (OnVersionChangeScanner<T>.MethodInfo info0 : jclass.onVersionChangeMethods) {
                final OnVersionChangeScanner<T>.VersionChangeMethodInfo info
                  = (OnVersionChangeScanner<T>.VersionChangeMethodInfo)info0;

                // Get Java model object
                if (jobj == null)
                    jobj = JTransaction.this.getJObject(id);

                // Invoke method
                info.invoke(jobj, oldVersion, newVersion, oldValuesByStorageId, oldValuesByName);
            }
        }
    }

// Convert methods

    @SuppressWarnings("unchecked")
    private <X, Y> Y convert(Converter<X, Y> converter, Object value) {
        return converter != null ? converter.convert((X)value) : (Y)value;
    }

    /**
     * Convert a value read from a core API field, possibly in an older version object, to the
     * corresponding {@link JSimpleDB} value, to the extent possible.
     */
    Object convertCoreValue(Field<?> field, Object value) {
        return value != null ? this.convert(field.visit(new CoreValueConverterBuilder()), value) : null;
    }

// CoreValueConverterBuilder

    /**
     * Builds a {@link Converter} for any core API {@link Field} that converts, in the forward direction, core API values
     * into {@link JSimpleDB} values, to the extent possible. In the case of reference and enum fields, the
     * original Java type may no longer be available; if not, values are converted to {@link UntypedJObject}
     * or left as {@link org.jsimpledb.core.EnumValue}s.
     *
     * <p>
     * Returns null if no conversion is necessary.
     * </p>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    class CoreValueConverterBuilder extends FieldSwitchAdapter<Converter<?, ?>> {

        // We can only convert EnumValue -> Enum if the Enum type is known and matches the old field's original type
        @Override
        public Converter<?, ?> caseEnumField(EnumField field) {
            final Class<? extends Enum<?>> enumType = field.getFieldType().getEnumType();
            return enumType != null ? EnumConverter.createEnumConverter(enumType).reverse() : null;
        }

        @Override
        public Converter<?, ?> caseReferenceField(ReferenceField field) {
            return new ReferenceConverter<JObject>(JTransaction.this, JObject.class).reverse();
        }

        @Override
        public <E> Converter<?, ?> caseSetField(SetField<E> field) {
            final Converter elementConverter = field.getElementField().visit(this);
            return elementConverter != null ? new NavigableSetConverter(elementConverter) : null;
        }

        @Override
        public <E> Converter<?, ?> caseListField(ListField<E> field) {
            final Converter elementConverter = field.getElementField().visit(this);
            return elementConverter != null ? new ListConverter(elementConverter) : null;
        }

        @Override
        public <K, V> Converter<?, ?> caseMapField(MapField<K, V> field) {
            Converter keyConverter = field.getKeyField().visit(this);
            Converter valueConverter = field.getValueField().visit(this);
            if (keyConverter != null || valueConverter != null) {
                if (keyConverter == null)
                    keyConverter = Converter.identity();
                if (valueConverter == null)
                    valueConverter = Converter.identity();
                return new NavigableMapConverter(keyConverter, valueConverter);
            }
            return null;
        }

        @Override
        public <T> Converter caseField(Field<T> field) {
            return null;
        }
    }

// ValidationListener

    private class ValidationListener implements AllChangesListener {

    // SimpleFieldChangeListener

        @Override
        public <T> void onSimpleFieldChange(Transaction tx, ObjId id,
          SimpleField<T> field, int[] path, NavigableSet<ObjId> referrers, T oldValue, T newValue) {
            this.revalidateIfNeeded(id, field, referrers);
        }

    // SetFieldChangeListener

        @Override
        public <E> void onSetFieldAdd(Transaction tx, ObjId id,
          SetField<E> field, int[] path, NavigableSet<ObjId> referrers, E value) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public <E> void onSetFieldRemove(Transaction tx, ObjId id,
          SetField<E> field, int[] path, NavigableSet<ObjId> referrers, E value) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public void onSetFieldClear(Transaction tx, ObjId id, SetField<?> field, int[] path, NavigableSet<ObjId> referrers) {
            this.revalidateIfNeeded(id, field, referrers);
        }

    // ListFieldChangeListener

        @Override
        public <E> void onListFieldAdd(Transaction tx, ObjId id,
          ListField<E> field, int[] path, NavigableSet<ObjId> referrers, int index, E value) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public <E> void onListFieldRemove(Transaction tx, ObjId id,
          ListField<E> field, int[] path, NavigableSet<ObjId> referrers, int index, E value) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public <E> void onListFieldReplace(Transaction tx, ObjId id,
          ListField<E> field, int[] path, NavigableSet<ObjId> referrers, int index, E oldValue, E newValue) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public void onListFieldClear(Transaction tx, ObjId id, ListField<?> field, int[] path, NavigableSet<ObjId> referrers) {
            this.revalidateIfNeeded(id, field, referrers);
        }

    // MapFieldChangeListener

        @Override
        public <K, V> void onMapFieldAdd(Transaction tx, ObjId id,
          MapField<K, V> field, int[] path, NavigableSet<ObjId> referrers, K key, V value) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public <K, V> void onMapFieldRemove(Transaction tx, ObjId id,
          MapField<K, V> field, int[] path, NavigableSet<ObjId> referrers, K key, V value) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public <K, V> void onMapFieldReplace(Transaction tx, ObjId id,
          MapField<K, V> field, int[] path, NavigableSet<ObjId> referrers, K key, V oldValue, V newValue) {
            this.revalidateIfNeeded(id, field, referrers);
        }

        @Override
        public void onMapFieldClear(Transaction tx, ObjId id, MapField<?, ?> field, int[] path, NavigableSet<ObjId> referrers) {
            this.revalidateIfNeeded(id, field, referrers);
        }

    // Internal methods

        private void revalidateIfNeeded(ObjId id, Field<?> field, NavigableSet<ObjId> referrers) {
            final JClass<?> jclass;
            try {
                jclass = JTransaction.this.jdb.getJClass(id);
            } catch (TypeNotInSchemaVersionException e) {
                return;
            }
            final JField jfield = jclass.getJField(field.getStorageId(), JField.class);
            if (jfield.requiresValidation)
                JTransaction.this.revalidate(referrers);
        }
    }
}

