## Introduction ##

JSimpleDB makes powerful persistence simple for Java programmers.

JSimpleDB's goal is to make Java persistence as **simple** as possible, doing so in a **Java-centric** manner, while remaining strictly **type safe**.

JSimpleDB does this without sacrificing flexibility or scalability by relegating the database to the simplest role possible - storing data as key/value pairs - and providing all other supporting features, such as indexes, command line interface, etc., in a simpler, type-safe, Java-centric way.

JSimpleDB also adds important new features that traditional databases don't provide.

In other words, for the most part JSimpleDB is not trying to create solutions where none existed before. Rather, it's trying to reimplement many of the same, well-tested ideas and algorithms used in traditional databases in a way that is simpler and more natural for Java programmers. So simplicity and maintainability are primary benefits, but as it happens, new solutions to some problems that traditional databases _don't_ solve for you also become easy and natural.

### Table of Contents ###



## A Persistence Layer ##

JSimpleDB is a _Java Persistence Layer_. It provides a Java-centric view of a transactional key/value database that you supply.

JSimpleDB includes adapters for existing databases, such as [LevelDB](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/leveldb/LevelDBKVDatabase.html), [Oracle's Berkeley DB Java Edition](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/bdb/BerkeleyKVDatabase.html), and any [SQL database](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/sql/package-summary.html), as well as [in-memory](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/simple/SimpleKVDatabase.html) and [XML flat file](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/simple/XMLKVDatabase.html) key/value stores for testing and prototyping.

### "Normal" Java vs. "Persistence" Java ###

JSimpleDB eliminates the distinction between "normal Java" and "persistence Java".

For example, JSimpleDB does not have or need a "query language". All data access is done through normal Java code, using objects and method calls.

Your "database schema" is defined by your Java classes and their fields. These implicitly define your one-to-one, many-to-one, and many-to-many relationships. JSimpleDB provides tightly controlled and well-defined schema versioning support so that you can safely refactor your code freely.

JSimpleDB also avoids design choices that would limit _scalability_. Some JSimpleDB design choices that support scalability are:
  * JSimpleDB runs on top of any database that can [look like](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/KVDatabase.html) a transactional key/value store
  * JSimpleDB avoids the use of "bottleneck" keys (e.g., auto-increment counters) that can cause high contention between transactions
  * JSimpleDB provides support not only for indexing fields, but also tools to support building your own custom indexes, so that virtually anything you frequently query can be indexed
  * JSimpleDB provides explicit support for rolling, no-downtime software updates that involve running different versions of your code (with different schemas) on different nodes at the same time
  * JSimpleDB does not include or require any operations that affect the entire database

Most importantly, JSimpleDB does all of this while remaining **completely type safe**.

## Quick Example ##

JSimpleDB itself is configured using just <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/package-summary.html'>a few Java annotations</a>. For example:
```java

@JSimpleClass
public abstract class Person implements JObject {

// My age
public abstract int getAge();
public abstract void setAge(int age);

// My friends
public abstract Set<Person> getFriends();
}```

Of note:
  * We are defining a database object type `Person` that has two fields:
    * An `int` field named `age` of type `int`
    * A `Set` field named `friends` with element type `Person`
  * There is no setter method for the `friends` set
    * Collection fields are initially empty but always _exist_; `getFriends()` will never return `null`
  * The model class is `abstract`
    * JSimpleDB will generate a concrete subclass that overrides the declared bean property methods at runtime
    * JSimpleDB ensures the generated subclass will implement <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JObject.html'><code>JObject</code></a>
  * `Person` is declared to implement the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JObject.html'><code>JObject</code></a> interface
    * This makes life easier, but there is no requirement that the model class extend or implement anything

### Field Types ###

JSimpleDB supports three kinds of fields: **simple** atomic fields, **complex** collection fields, and lock-free **counter** fields.

#### Simple Fields ####

A simple field is any field that can be considered as a single atomic value and encoded into binary form. This inclues primitive types and primitive wrapper types, `String`, other usual suspects such as `Date`, `UUID`, etc., and any `Enum` type.

All simple fields have a well-defined sort order, which is also reflected in their binary encodings in the key/value store.

Simple fields with **array type** up to 255 dimensions are also supported. Array types are read and written atomically by value.

Simple fields also include **reference** fields, which are fields that refer to other Java database objects.

You can define your own simple types by providing a class annotated with <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/JFieldType.html'><code>@JFieldType</code></a> that implements the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/FieldType.html'><code>FieldType</code></a> interface.

#### Complex Fields ####

The supported complex field types are `Set` (actually `NavigableSet`), `List`, and `Map` (actually `NavigableMap`). JSimpleDB includes explicit support for these types, including indexing, change notification, etc.

The complex field types support any simple field type for their **sub-field(s)**, i.e., the `Set` and `List` element, and `Map` key and value.

In the case of complex fields with primitive sub-field type, both true primitive and primitive wrapper types are supported. In the former case, null values are not allowed:
```java

@JSimpleClass
public abstract class Person implements JObject {

public abstract Set<Integer> wrapperSet();      // element type is java.lang.Integer

@JField(type = "int")
public abstract Set<Integer> primitiveSet();    // element type is int

public void test() {
this.wrapperSet().add(null);        // OK
this.primitiveSet().add(null);      // IllegalArgumentException!
}
}```

#### Counter Fields ####

<a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/CounterField.html'><code>Counter</code></a> fields are a special type optimized for lock-free addition/subtraction, allowing a high degree of concurrency. Counter fields contain a 64-bit counter value.

## Accessing Database Objects ##

Once you have defined your classes, programming with JSimpleDB is all normal Java programming. There is no "query language".

A <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JTransaction.html'><code>JTransaction</code></a> represents a JSimpleDB transaction. The `JTransaction` associated with the current thread is available from the static method `JTransaction.getCurrent()`.

All state associated with JSimpleDB objects lives in the `JTransaction`; the model objects themselves are stateless. When you access a field of a model object, you are implicitly accessing that field's value in the `JTransaction` associated with the current thread. If there is no `JTransaction` open in the current thread, an `IllegalStateException` is thrown.

However, the Java model objects do not change from transaction to transaction like with JPA. You will get, and can use, the exact same `Person` object over and over again in multiple transactions. Of course, the person's fields may have different values in different transactions.

If you try to access a `Person` object in a new transaction when that `Person` has been deleted in some earlier committed transaction, you get a `DeletedObjectException`. You can check for this condition via `JObject.exists()`, and (if need be) recreate the object (with all fields reset) via `JObject.recreate()`.

### Finding Objects ###

To get all persistent `Person`s:

```java

// Get all people
public static Set<Person> getAll() {
return JTransaction.getCurrent().getAll(Person.class);
}```

The parameter to `JTransaction.getAll()` can be any Java type, including interface types, e.g.:
```java

// Get all objects that implement the HasName interface
public static Set<HasName> getAllNamed() {
return JTransaction.getCurrent().getAll(HasName.class);
}```

All objects have an internal 64-bit object ID which is represented by the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/ObjId.html'><code>ObjId</code></a> class. Typically you would not access objects by object ID but you can if you want to:

```java

public static Person find(ObjId id) {
return JTransaction.getCurrent().getJObject(id, Person.class);
}```

### Object Lifecycle ###

To create a `Person`, invoke `create()` on a <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JTransaction.html'><code>JTransaction</code></a> object, e.g.:

```java

// Create a Person
public static Person create() {
return JTransaction.getCurrent().create(Person.class);
}```

To delete a `Person`:

```java

person.delete();```

The `exists()`, `delete()`, and `recreate()` methods are part of the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JObject.html'><code>JObject</code></a> interface, which all generated model subclasses implement. Your classes don't have to to be declared to implement `JObject`, but life is a easier if they are.

### Delete Action and Delete Cascade ###

In regular Java, you can't explicitly delete objects, you can only unreference them. Since they are unreferenced, there is by definition no issue with other objects still referring to them.

However, all persistence layers that support references between "objects" must decide with what to do when deleting an object which is either referenced by, or itself references, other objects that still exist.

#### Delete Action ####

First we consider the situation where an object is deleted, but that object still has one or more other objects referencing it. In SQL we have `ON DELETE ...` for this situation.

In JSimpleDB, every reference field is configured with a <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/DeleteAction.html'><code>DeleteAction</code></a> to be taken when a still referred-to object is deleted. There are four choices:

  * `EXCEPTION` - Disallow deletion of such an object; instead, throw `ReferencedObjectException`. This is the default value.
  * `UNREFERENCE` - Set all references to the object to null (in the case of simple reference fields) or remove the corresponding entry (in the case of sets, lists, and maps).
  * `DELETE` - Delete all objects referring to the deleted object, repeating recursively as necessary. Cycles in the reference graph are handled correctly.
  * `NOTHING` - Do nothing; subsequent attempts to dereference the deleted object will result in a `DeletedObjectException`.

Note the subtle difference between SQL's `ON DELETE SET NULL` and `DeleteAction.UNREFERENCE`. For simple fields they work the same, but for collection fields, the reference is _removed_ from the collection rather than being set to null.

To be be guaranteed to never see any `DeletedObjectException`s, you can do the following:
  1. Avoid configuring reference fields with `DeleteAction.NOTHING`
  1. Always check `exists()` before using a Java model object that has been brought in from outside the transaction

#### Delete Cascade ####

JSimpleDB also allows object deletion to cascade to other objects referenced by the deleted object. If so configured, these other objects will also be deleted, and the cascade will proceed recursively if needed (cycles in the graph of references are handled correctly).

See <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/JField.html'><code>@JField</code></a> for details on configuring the delete behavior for reference fields.

### Indexes ###

Given the above `Person` class with no indexes configured, if you wanted to calculate whether any `Person` having a specific age existed, you would have to write this code:
```java

public boolean existsPersonOfAge(int age) {
for (Person person : Person.getAll()) {
if (person.getAge() == age)
return true;
}
return false;
}```

Having to write that code is a _good_ thing: JSimpleDB is exposing the underlying performance reality and forcing the developer to confront the fact that the cost of calculating this function is an iteration over every `Person` in the database. With other persistence layers such as JPA, this cost would be hidden - the number of database rows visited is decided elsewhere and completely non-obvious from looking at Java code.

With a large database, such a whole database iteration could be impractically slow. At least with JSimpleDB it will be slow for an obvious reason, not mysteriously so. An explicit goal of JSimpleDB is that it not be possible to have queries be horribly slow unless you write them that way yourself!

Of course this is the perfect situation for an **index** on the `age` field. Here we create one by adding `indexed = true` to the `@JField` annotation:

```java

// My age - now indexed!
@JField(indexed = true)
public abstract int getAge();
public abstract void setAge(int age);

public boolean existsPersonOfAge(int age) {
return JTransaction.getCurrent().queryIndex(
Person.class, "age", Integer.class).asMap().containsKey(age);
}```

Now it's obvious by inspection that this query will run efficiently.

An index on a single field is basically just a mapping from each field value to the set of all objects having that value in the field. JSimpleDB simple indexes implement the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/index/Index.html'><code>Index</code></a> interface. This allows viewing the index as either a map from value to the set of objects with that value in the field:

```java

public static NavigableMap<Integer, NavigableSet<Person>> queryPersonAges() {
return JTransaction.getCurrent().queryIndex(Person.class, "age", Integer.class).asMap();
}```

...or as a set of (value, object) pairs:

```java

public static NavigableSet<Tuple2<Integer, Person>> queryPersonAges() {
return JTransaction.getCurrent().queryIndex(Person.class, "age", Integer.class).asSet();
}```

The `Person.class` and `Integer.class` parameters accomplish two goals: first, they provide compile-time type safety and allow JSimpleDB to verify the actual indexed types at runtime. Secondly, they allow you to narrow or widen the type to suit your needs.

For example, if `HappyPerson extends Person` and you only want to find happy people of a certain age, you can do this:

```java

public static NavigableMap<Integer, NavigableSet<HappyPerson>> queryHappyPersonAges() {
return JTransaction.getCurrent().queryIndex(HappyPerson.class, "age", Integer.class).asMap();
}```

Or, suppose your schema has evolved over time and the old superclass `Mammal` which `Person` used to extend no longer exists, but you still have some leftover non-human `Mammal`'s in your database and you want to include them in the index query. Then you can do this:

```java

public static NavigableMap<Integer, NavigableSet<JObject>> queryObjectAges() {
return JTransaction.getCurrent().queryIndex(JObject.class, "age", Integer.class).asMap();
}```

Since your old `Mammal` class is long gone, the old `Mammal` objects will appear as <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/UntypedJObject.html'><code>UntypedJObject</code></a>'s. You can still access their fields by directly invoking the corresponding `JTransaction` field access method.

<b>In all cases, JSimpleDB guarantees type safety</b>, even in the face of arbitrary refactoring of your code over time.

Since index maps are `NavigableMap`s, you can efficiently find the minimum or maximum value, create a restricted range of values, iterate values in forward or reverse order, etc.

```java

public static int getMaximumAge() {
NavigableMap<Integer, NavigableSet<Person>> index = Person.queryPersonAges();
return !index.isEmpty() ? index.lastKey() : -1;
}```

Since we have a `NavigableSet<Person>`, you may be wondering what sort order applies to `Person`. JSimpleDB sorts database objects by their type (i.e., Java class), and then by their unique object ID (the object ID is available via `JObject.getObjId()`). Object IDs are opaque 64-bit values. Newly created objects get a randomly generated value which avoids distributed database contention that would otherwise occur with an auto-increment counter.

#### Complex Field Indexes ####

You can also index complex fields, by indexing the `element` sub-field of a `Set` or `List`, or the `key` or `value` sub-field of a `Map`. For example:
```java

@JSimpleClass
public abstract class Student implements JObject {

// Database Fields

// The classes this student is attending
@JSetField(element = @JField(indexed = true))
public abstract Set<LectureClass> getLectureClasses();

// This student's ranking of his/her teachers
@JListField(element = @JField(indexed = true))
public abstract List<Teacher> getTeacherRankings();

// This student's test scores
@JMapField(key = @JField(indexed = true), value = @JField(indexed = true))
public abstract Map<Test, Float> getTestScores();

// Index Query Methods

// Map classes to students in the class
public static NavigableMap<LectureClass, NavigableSet<Student>> queryStudentsByLectureClass() {
return JTransaction.getCurrent().queryIndex(
Student.class, "lectureClasses.element", LectureClass.class).asMap();
}

// Map teacher to students that rank the teacher
public static NavigableMap<Teacher, Student> queryStudentsRankings() {
return JTransaction.getCurrent().queryIndex(
Student.class, "teacherRankings.element", Teacher.class).asMap();
}

// Map tests to students who have taken the test
public static NavigableMap<Test, NavigableSet<Student>> queryStudentsByTest() {
return JTransaction.getCurrent().queryIndex(
Student.class, "testScores.key", Test.class).asMap();
}

// Map tests scores to the students who got that score on some test
public static NavigableMap<Float, NavigableSet<Student>> queryStudentsByTestScore() {
return JTransaction.getCurrent().queryIndex(
Student.class, "testScores.value", Float.class).asMap();
}
}```

In JSimpleDB, reference fields are always indexed (analogous to SQL, where an index is required for foreign key constraints). So in the above example, <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/JField.html'><code>@JField</code></a> annotations are actually unnecessary except for `testScores.value`.

Indexes are a perfect way to provide Java access to both sides of a one-to-many, many-to-one, or many-to-many relationship without there having to actually be any redundant information: you just query the index for the "inverse" side of the relationship:

```java

@JSimpleClass
public abstract class User implements JObject {

// Get this user's account
public abstract Account getAccount();
public abstract void setAccount(Account account);
}

@JSimpleClass
public abstract class Account implements JObject {

// Get all users with this account
public NavigableSet<User> getUsers() {
return this.getTransaction().queryIndex(
User.class, "account", Account.class).asMap().get(this);
}
}```

Contrast with JPA, where it's possible for e.g., `parent.getChildren()` and `child.getParent()` to get out of sync.

In JSimpleDB, indexes are always up-to-date, reflecting the current transaction state.

#### Composite Indexes ####

JSimpleDB also supports composite indexes. A composite index is an index on more than one field. Composite indexes are mainly useful when you need to efficiently sort on multiple fields at once:
```java

@JSimpleClass(compositeIndexes =
@JCompositeIndex(name = "byName", fields = { "lastName", "firstName" }))
public abstract class Person implements JObject {

public abstract String getLastName();
public abstract void setLastName(String lastName);

public abstract String getFirstName();
public abstract void setFirstName(String firstName);

// Get Person's sorted by last name, then first name
public static Index2<String, String, Person> queryByLastNameFirstName() {
return JTransaction.getCurrent().queryCompositeIndex(Person.class, "byName", String.class, String.class);
}
}```

A composite index on two fields is returned as an <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/index/Index2.html'><code>Index2</code></a>, a composite index on three fields is returned as an <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/index/Index2.html'><code>Index3</code></a>, etc.

Any higher-order index can be viewed as a lower-ordered index on any prefix of its indexed fields, e.g.:
```java

// Maps each last name to the set of all associated first names
public static NavigableMap<String, NavigableSet<String>> queryNamesLastFirst() {
return Person.queryByLastNameFirstName().asIndex().asMap();
}```

#### Non-Unique Complex Field Indexes ####

The `List` element and `Map` value complex sub-fields are special, because their values are not unique to the field. In other words, the same value can appear more than once in a `List` or as a `Map` value.

Therefore, indexes on these two complex sub-fields can also be viewed as a type of composite index, where the "extra" field is the distinguishing value, i.e., the list index or map key.

For example:
```java

@JSimpleClass
public abstract class Student implements JObject {

// This student's ranking of his/her teachers
public abstract List<Teacher> getTeacherRankings();

// This student's test scores
@JMapField(value = @JField(indexed = true))
public abstract Map<Test, Float> getTestScores();

// Map teacher to students that rank the teacher AND the coresponding rank(s)
public static Index2<Teacher, Student, Integer> queryStudentsRankings() {
return JTransaction.getCurrent().queryListElementIndex(     // instead of querySimpleField()
Student.class, "teacherRankings.element", Teacher.class);
}

// Map tests scores to the students who got that score on some test AND the test(s)
public static Index2<Float, Student, Test> queryStudentsByTestScore() {
return JTransaction.getCurrent().queryMapValueIndex(        // instead of querySimpleField()
Student.class, "testScores.value", Float.class, Test.class);
}
}```

### Joins ###

Back to our indexed `age` field, now suppose the question you need to frequently answer is not whether any person exists of a specific age, but whether a specifc `Person` has any _friend_ of a specific age.

We just need to somehow intersect the set of that `Person`'s friends with the set of people of a specified age. This is the equivalent of an SQL `INNER JOIN`. JSimpleDB provides a clean way to do this.

First we need to make sure `friends` is declared as a `NavigableSet` (which is what it really is):

```java

// My friends
public abstract NavigableSet<Person> getFriends();```

Then we just do the "join" via set intersection:

```java

// Get all of my friends who are the specified age
public NavigableSet<Person> getFriendOfAge(int age) {

// Get all Person's of age 'age', whoever they are
final NavigableSet<Person> peopleOfAge = this.queryPersonAges().get(age);
if (peopleOfAge == null)
return NavigableSets.<Person>empty();

// Get all of my friends
final NavigableSet<Person> myFriends = this.getFriends();

// Return the intersection
return NavigableSets.intersection(peopleOfAge, myFriends);
}```

Reasoning about sets and operations like intersection, union, and difference is straightforward. The <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/util/NavigableSets.html'><code>NavigableSets</code></a> utility class provides efficient methods for set intersection, union, and difference. The sets must have comparable elements and a consistent sort order for those elements (this property is what allows the intersection operation to be efficient). JSimpleDB provides this consistent ordering for you in all of its returned sets.

### Reference Inversion ###

In JSimpleDB reference fields, including sub-fields of a collection field, are always indexed. Therefore, unlike in normal Java programming, we can efficiently determine what objects (if any) refer to a given object:

```java

// Who considers me a friend?
public Set<Person> whoConsidersMeAFriend() {
return this.getTransaction().queryIndex(
Person.class, "friends.element", Person.class).get(this);
}```

More generally, we can determine what objects refer to a given object indirectly, through an arbitrary sequence of reference fields. Such a sequence is called a **reference path**, and JSimpleDB exposes this functionality via `JTransaction.invertReferencePath()`:

```java

// Who considers me a friend?
public Set<Person> whoConsidersMeAFriend() {
return this.getTransaction().invertReferencePath(
Person.class, "friend", Collections.singleton(this));
}

// Who considers any of my friends a friend of a friend (possibly someone other than me)?
public Set<Person> whoConsidersAnyOfMyFriendsAFriendOfAFriend() {
return JTransaction.getCurrent().invertReferencePath(
Person.class, "friend.friend", this.getFriends());
}```

A reference path is simply a chain of reference fields via which one can hop through the graph defined by objects (nodes) and references (edges). Following a reference path in the forward direction is what one normally does in Java. JSimpleDB allows you to also invert reference paths, so now you can go in either direction. As always, type safety is guaranteed.

When inverting reference paths, JSimpleDB eliminates duplicates (caused by multiple routes to the same destination) as soon as possible. However, there's no magic surrounding how reference paths are followed. If you're not careful, you can create a combinatorial explosion when there is a high degree of fan-in.

Reference paths are also the basis of the powerful `@OnChange` annotation, described below.

### Snapshot Transactions ###

JSimpleDB transactions can be thought of as containers for the state of objects. As with any other transactional database, when the transaction closes that state is no longer available.

In the case of JPA, some state may be available after a transaction (detached objects), but exactly which state is often difficult to control or determine.

To allow you to keep a "snapshot" of some portion of the transaction state after that transaction has closed, and be able to specify exactly how much information to "snapshot", JSimpleDB provides <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/SnapshotJTransaction.html'>snapshot transactions</a>. Snapshot transactions are independent, in-memory transactions that persist as long as you keep a reference to them.

Snapshot transactions are initially empty; you can copy objects between any two transactions (whether "real" or "snapshot) using the `JObject` methods `copyIn()` and `copyOut()`. These methods allow you to specify precisely how much information you want to copy, either as a list of reference paths, or by explicitly providing an `Iterable` containing the objects to copy. When copying data between transactions, the copies are performed efficiently at the key/value level, and cycles in the graph of object references are handled correctly.

Unlike in JPA, where detached objects are just plain objects, a snapshot transaction is a fully functional transaction and supports all JSimpleDB features such as index queries, reference inversion, validation, change notification, etc. As a result, your code works the same either way.

Snapshot transactions make a perfect foundation for user interface presentation models. You can create multiple independent snapshot transactions if needed and keep them around as long as you like. The only thing you can't do with a snapshot transaction is `commit()` it.

### Storage IDs ###

Underneath the covers, every JSimpleDB model class, field, and composite index has a unique **storage ID**. Internally, database identity and overall schema structure are defined by storage ID's, not names. The storage ID's, in encoded form, are used to prefix keys in the underlying key/value store.

In other words, storage IDs provide a level of indirection between your Java code and database identity, allowing your Java code to change more freely while also providing a flexible, controlled and well-defined way to update database contents (if necessary) when the schema does change. See [Schema Management](#Schema_Management.md) for more info on schema updates.

Unless you specify them explicitly, JSimpleDB will auto-generate storage ID's for you based on the JSimpleDB name of the class, field, or composite index. So you normally do not need to bother with storage ID's.

For classes and fields, the JSimpleDB name defaults to the class's simple name or the field's Java bean property name. Therefore, by default changing a class or getter method name will result in a schema change. To avoid unnecessary schema changes after simple name changes, you can explicitly set the name or the storage ID to its previous value in the corresponding annotation.

The way that storage ID's are encoded requires fewer bytes for smaller values. Values up to 250 only require one byte; values up to 506 only require two bytes, values up to 65,786 only require three bytes, etc. The default auto-generation of storage ID's results in values that encode in three bytes, i.e., in the range 507-65786. To save a few bytes, you can assign storage ID's explicitly if you want to:
```java

@JSimpleClass(storageId = 100)
public abstract class Person implements JObject {

// My age
@JField(storageId = 101)
public abstract int getAge();
public abstract void setAge(int age);

// My friends
@JSetField(storageId = 102, element = @JField(storageId = 103))
public abstract Set<Person> getFriends();
}```

## Indexing and Detecting Changes ##

### Indexing in the General Sense ###

A database _index_ is just a special case of _derived information that is kept up to date automatically for you by the database_. The key benefits of using database indexes are:
  * Much better performance - usually constant time queries of the indexed information
  * The derived information is kept up-to-date automatically
  * The database hides all the implementation details, keeping your application logic clean

With JSimpleDB you can easily build your own arbitrary indexes, based on any derived information, that also satisfy these criteria.

Traditionally, in general this is hard part because it means tracking down all the places in your code that could possibly modify the information from which your index is derived and adding hooks in all those places. JSimpleDB makes solving this problem much easier.

With traditional databases, sometimes you can make it work when the information is all contained in one object: then you can add intercept/update code into the setter methods of all the fields that affect the derived information. This extra code effectively serves as a trap for change notification, where it then updates the derived information.

Or if you're lucky, your derived information matches an SQL built-in aggregate function like `MAX()` for which SQL databases often have built-in functions. Often these built-in functions rely on "secret" internal indexes that make the function fast. But even if so, that hack only works when your query runs `MAX()` over the entire table.

Thinking more generally, what if your "index" is derived for many objects, possibly ones that are far away from each other? Suppose for example you need to be able to efficiently calculate the average age of _all the friends_ of any `Person`. There's no way in SQL to tell the database to keep an index of the average age of each `Person`'s friends, and an SQL `AVG()` query will need to iterate through all the friends to calculate the average. With today's huge datasets, iterating through every element of a collection may not be an option. So you have to consruct and maintain this index yourself.

This is an important subtlety that affects scaling applications written using SQL databases: if what you want to index can't be indexed by a capability built-in to the database, but your dataset is too large to _not_ index the information you need, then suddenly you have "index" logic spamming your Java codebase.

In our present example, you would have to track down any code that either (a) alters the age of a `Person` (and then figure out who that `Person` is a friend of), or (b) changes any `Person`'s set of friends. But clearly the logic for maintaining a database index, which is nothing but derived information, belongs at the data layer, not the service or business layer.

### `@OnChange` ###

With JSimpleDB, you can index virtually anything, based on any reachable information, using the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnChange.html'><code>@OnChange</code></a> annotation. The `@OnChange` annotation allows you to monitor arbitrarily distant changes by specifying the reference path to the change you want to monitor.

To maintain an index in JSimpleDB:
  1. Detect relevant changes via `@OnChange`-annotated methods
  1. Update the derived information when a change occurs

Most importantly, the code that implements #1 and #2 above all lives in one Java class.

In the example below, we monitor for changes in our set of friends and their ages, and update the average age automatically. This works no matter how the age is modified or where else those changes might be made in your codebase.
We keep all the code to maintain our custom "friends' average age" index in one place where it belongs - in the data object.

```java

// Get my friends' average age - value is always up to date!
public double getFriendsAverageAge() {
return (double)this.getFriendsAgeSum() / this.getNumFriends().get();
}

// Keep track of how many friends I have (without locking!)
protected abstract Counter getNumFriends();

// Keep track of the sum of my friends' ages
protected abstract long getFriendsAgeSum();
protected abstract void setFriendsAgeSum(long ageSum);

// Notify me when any of my friend's ages changes
@OnChange("friends.element.age")
private boolean onFriendAgeChange(SimpleFieldChange<Person, Integer> change) {
this.setFriendsAgeSum(this.getFriendsAgeSum() - change.getOldValue() + change.getNewValue());
}

// Notify me when a friend is added
@OnChange("friends")
private boolean onFriendsAdd(SetFieldAdd<Person, Person> change) {
this.setFriendsAgeSum(this.getFriendsAgeSum() + change.getElement().getAge());
this.getNumFriends().adjust(1);
}

// Notify me when a friend is removed
@OnChange("friends")
private boolean onFriendsRemove(SetFieldRemove<Person, Person> change) {
this.setFriendsAgeSum(this.getFriendsAgeSum() - change.getElement().getAge());
this.getNumFriends().adjust(-1);
}

// Notify me when friends is cleared
@OnChange("friends")
private boolean onFriendsClear(SetFieldClear<Person> change) {
this.getNumFriends().set(0);
this.setFriendsAgeSum(0);
}```

This index is maintained as efficiently as possible: we are notified only when a meaningful change occurs, and the update is incremental (constant time) and immediate.

The average age is a simple example, but the indexed information can be arbitrary, e.g., the sum of your friends ages modulo 23, or whatever.

By the way, JSimpleDB does not notify for "changes" that don't actually change anything, such as `person.setAge(21)` when the age equals `21`, or `person.getFriends().add(friend)` when `friend` is already in the set. Also, each object is only notified once about any particular change, even if the change is visible through multiple different routes through the reference path (this would necessarily involve a collection field somewhere on the path).

Of course, `@OnChange` notifications are handy for lots of other purposes besides custom indexes. For example, you might want to monitor some condition involving several related objects and generate an alert, etc. `@OnChange` notifications are also handy for validation scenarios involving distant dependencies (see [Validation](#Validation.md)).

#### Calculating Sizes ####

Notice in the example we are tracking the size of the `friends` set manually. Why not just use `this.getFriends().size()`?

In JSimpleDB invoking `size()` on a `Set` or `Map` requires an _O(n)_ time interation through the collection, which for very large collections can be a slow operation (invoking `size()` on a list is constant time, because list elements are explicitly indexed; lists have performance characteristics similar to `ArrayList`).

This may seem dumb - why not just keep track of the size? There is an important reason: allowing for reduced contention in distributed databases.

Imagine a database with many distributed nodes on the network, and on each node an application is rapidly adding new objects to some `Set`. If JSimpleDB maintained a hidden `size` field with each collection, then that field would have to be updated with each insert, and therefore would be highly contended (i.e., causing a bottleneck), causing the database to be slow. Without it, however, the set elements can be added concurrently without conflict.

JSimpleDB leaves it up to you whether the size of a collection should be explicitly maintained. After all, the size of a collection is just another type of index (derived information).

To support such usage, JSimpleDB provides the `Counter` field type, which holds a `long` value and can be mutated via addition/subtraction without actually reading the value; on many key/value stores, this operation can be performed entirely without locking.

### Lifecycle Notifications ###

JSimpleDB provides notifications whenever a database object is created or deleted via the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnCreate.html'><code>@OnCreate</code></a> and <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnDelete.html'><code>@OnDelete</code></a> annotations:

```java

@OnCreate
private void gotCreated() {
System.out.println("Hello world");
}

@OnDelete
private void gotDeleted() {
System.out.println("Goodbye world");
}```

Note that `@OnCreate` is not the same event as object construction. For exmaple, a deleted object can be `recreate()`d, in which case `@OnCreate` will be invoked again but no Java construtor will be invoked because the representative Java object already exists.

## Schema Management ##

Traditional SQL databases provide no explicit support for managing schema changes. This used to be OK when you had a single application server running on a single database, and it was small enough that running a few `ALTER TABLE` statements after a restart was relatively quick.

Today, however, not only are the data sets too large for schema changes that lock an entire table, it's often not acceptable to bring down all of the application servers at the same time, even if you _could_ upgrade them all quickly. Instead, a rolling upgrade is required, where during the upgrade period different application servers will be running different versions of the software. Therefore, at some point you are going to have two different versions of your application running on two different nodes, both reading from and writing to the same database, but expecting to see and use different schemas. This situation presents obvious challenges and few databases provide any real solution.

Some NoSQL databases duck this question by being "schemaless", which really means "the schema is your problem, not mine". Other databases support the notion of an object version, but leave the rest to the application developer.

An important goal of JSimpleDB is to provide a first class solution to this problem by providing explicit support to the application for schema maintenance and migration. In addition, the code for handling schema migration should live only in the data layer and not pollute the rest of the application. And finally, as always Java type safety should never be violated.

This support has several aspects.

### Schema Tracking ###

JSimpleDB databases have an explicit notion of a **schema** as well as schema **version numbers**, and you must explicitly tell JSimpleDB what schema version number you are using before you can do anything with a database. If the schema you are intending to use conflicts with what's recorded in the database, you will get an error. JSimpleDB simply won't let you inadvertently read or write data in an incompatible way.

A schema is defined as a set of object types, the fields in those object types, and any associated composite indexes. Each object type, field, and composite index is identified by its unique storage ID. Therefore, changes only to the names of things do not require a schema change.

Internally, schemas are represented by <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/schema/SchemaModel.html'><code>SchemaModel</code></a> objects. This object type has an XML representation which is used to serialize the schema into the database. Normally, you don't need to mess with `SchemaModel`s - they are generated for you from your annotated classes and handled automatically. But methods exist to allow you to access and introspect the current and all previously recorded database schemas if necessary.

JSimpleDB allows the following changes between schema versions:
  * Adding and removing object types
  * Adding and removing fields
  * Adding or removing indexing from a field
  * Adding and removing composite indexes

The thing you can't do is re-use a storage ID in a different way. For example, if storage ID `72` is used to identify a `String` field in schema version X, then any other schema version Y that uses storage ID `72` must also use it to identify a `String` field.

JSimpleDB does allow a field storage ID to be reused in different object types. This facilitates moving fields around in the Java type hierarchy. For example, if a schema change refactors the `Vehicle` class, replacing it with `Car` and `Truck` classes, then an existing `licensePlate` field could be copied into both `Car` and `Truck` without change -- or simply put into a common `Vehicle` abstract superclass. Then, for example, an index on the `licensePlate` field would contain both `Car`s and `Truck`s.

### Schema Verification ###

Within each JSimpleDB database is recorded the version and associated `SchemaModel` of every schema that has ever been used to write new objects into that database in the past. When a JSimpleDB database is initialized, a schema is generated from your annotated classes, but you must specify the version number of that schema, and the first thing that occurs in each new transaction is a (quick) verification that the provided schema matches the schema with that version number recorded in the database.

If there is a mismatch, an `InvalidSchemaException` exception is thrown. If the database has no record of that schema version, and you have configured JSimpleDB to allow recording new schemas, it will write the new schema into the database and proceed normally. If JSimpleDB is configured to not allow recording new schemas, an exception is thrown (more on when to enable this setting below).

### Object Versions ###

In addition to tracking the schemas, JSimpleDB also records with each object the version of the schema according to which the object was written. This tells us what fields are part of that particular object.

So now imagine two different application servers talking to the same database, but using two different schema versions. If they are both creating objects, then the database might contain objects of the same type (i.e., with the same storage ID) but having different versions and therefore containing different fields.

Here is what happens when an object with version X is read by the application server using schema version Y:

  1. The object is automatically upgraded (or downgraded) from schema version X to schema version Y
    * Newly removed fields are removed (pending step #2)
    * Newly added fields are initialized to their default values
    * All other fields stay the same
    * Any associated indexes are updated as necessary
  1. Any <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a>-annotated methods in the affected object are invoked, with these parameters:
    * The old schema version
    * The new schema version
    * Either a `Map<String, Object>` containing all of the old field values indexed by name, or a `Map<Integer, Object>` containing all of the old field values indexed by storage ID (depending on the method's declared parameters)

So the `@OnVersionChange` notification allows the object to handle the upgrade in a controlled manner. For example, suppose your application is at release 1.0 and uses schema version #1 which has this field containing a `Person`'s name in the form _Last, First_:

```java

public abstract String getName();
public abstract void setName(String name);```

But in application release 2.0 you decided you needed separate last and first names, so you create schema version #2, replacing the `name` field with `lastName` and `firstName` fields. Then your release 2.0 code, with a new method to handle the schema change, might look like this:

```java

public abstract String getLastName();
public abstract void setLastName(String lastName);

public abstract String getFirstName();
public abstract void setFirstName(String firstName);

@OnVersionChange(oldVersion = 1, newVersion = 2)
private void splitNameField(Map<String, Object> oldValues) {
final String name = (String)oldValues.get("name");
final int comma = name.indexOf(',');
this.setLastName(name.substring(0, comma).trim());
this.setFirstName(name.substring(comma + 1).trim());
}```

The `splitNameField()` method will be invoked when the `Person` object is upgraded, which happens just prior to the first field access. It ensures that the field is forward-migrated properly, and none of your other release 2.0 code will ever need to know anything about prior schema versions.

Object versions are themselves indexed; you can access this index via `JTransaction.queryVersion()`. This makes it easy to implement a one time process that proactively visits every object that needs to be upgraded.

### Schema Migration ###

The astue reader may notice that in a "no downtime" world, even adding the above `@OnVersionChange` handler is insufficient. That's because _backward_ migration may also occur, for example, when you have upgraded some (but not all) of your servers to release 2.0, and a yet-to-be-upgraded release 1.0 server needs to read an object written by an already-upgraded release 2.0 server.

The combination of multiple nodes, no application downtime, and incremental upgrades involving incompatible schemas makes things tricky indeed.

The answer is a multi-step migration process. First, create an intermediate software release 1.5 containing the downward migration `@OnVersionChange` handler:

```java

public abstract String getName();
public abstract void setName(String name);

@OnVersionChange(oldVersion = 2, newVersion = 1)
private void joinNameField(Map<String, Object> oldValues) {
this.setName(oldValues.get("lastName") + ", " + oldValues.get("firstName"));
}```

This new intermediate release 1.5 of your software still uses the original schema version #1, but it is now prepared to handle a schema version #2 object if it encounters one. Again, this handling is contained entirely in the data object.

Now your upgrade rolls out in two phases:

  1. Upgrade all machines from version 1.0 to 1.5
  1. Upgrade all machines from version 1.5 to 2.0

During phase 1, all objects stay at schema version #1. During phase 2, objects are upgraded and downgraded as necessary as different servers access them. Once phase 2 is complete, all future object accesses will use schema version #2, upgrading old version #1 objects on demand over time. Or, you can run a one-time scan that upgrades objects using `JObject.upgrade()`.

Voilà - an incremental, multi-node rolling application upgrade across releases using incompatible schema versions and with no downtime.

Note that release 2.0 of your software will need to configure JSimpleDB to allow the addition of new schemas. Once phase 2 is complete, this setting can be turned back off.

The JSimpleDB CLI utility allows you to inspect all schema versions recorded in a database. You can also remove any obsolete schemas when there are no more remaining objects with that version.

## Validation ##

JSimpleDB supports automatic incremental verification of JSR 303 validation constraints. By "incremental" we mean only those objects containing a field that has actually changed are (re)validated. Each time an object field is changed, the object is added to an internal validation queue (if not already on it). The validation queue is processed automatically on `commit()`, and if validation fails, a `ValidationException` is thrown.

What is described above is the same as supplied by e.g., JPA. However, JSimpleDB also adds a few additional useful features.

  * You can manually add any object to the validation queue by invoking `revalidate()` on that object.
  * You can trigger processing of the validation queue at any time via `JTransaction.validate()`, or empty the queue via `JTransaction.resetValidationQueue()`
  * You can supply custom validation logic by annotating a method with `@Validate`. All such methods will be invoked when the object is validated.
  * Combining `@Validate` with `@OnChange` allows you to validate complex, multi-object constraints (see below)

Note that JSimpleDB validates on a per-object basis. Therefore, the `@Valid` constraint, which causes validation to recurse through reference(s) to other objects, is redundant and rarely needed. Instead, ensure each individual object is added to the validation queue as necessary when changed.

### Configuring Validation ###

When creating a new JSimpleDB transaction, you specify the `ValidationMode` for the transaction. The default is `AUTOMATIC` which gives the behavior described above. The available options are:
  * `DISABLED` - no validation will be performed, even if you invoke `JTransaction.validate()` explicitly
  * `MANUAL` - validation is only performed when you invoke `JTransaction.validate()` explicitly; changes to fields with JSR 303 annotations do _not_ enqueue the object for validation
  * `AUTOMATIC` - validation is performed when you invoke `JTransaction.validate()` explicitly, and automatically on `commit()`; changes to fields with JSR 303 annotations automatically enqueue the object for validation

### Complex Validation ###

Although `javax.validation.constraints` provides many handy validation constraints, there are often cases that require validation of more complex constraints that depend on more than one field, or even multiple objects at the same time.

For example, suppose each `Person` may have both `friends` and `enemies` and you have a constraint that says nobody can be both a friend and an enemy. This kind of constraint is difficult to handle efficiently with traditional ORM solutions.

With JSimpleDB, this is easy to do by following these steps:
  1. Add a `@Validate`-annotated method that checks the constraint
  1. Detect relevant changes via `@OnChange` and enqueue for validation

Here's an example:

```java

@OnChange("friends")
private boolean onFriendsChange(SetFieldChange<Person> change) {
this.revalidate();
}

@OnChange("enemies")
private boolean onEnemiesChange(SetFieldChange<Person> change) {
this.revalidate();
}

@Validate
private void checkForFrenemies() {
if (!NavigableSets.intersection(this.getFriends(), this.getEnemies()).isEmpty())
throw new ValidationException(this, "we don't allow frenemies");
}```

An important point here is that the validation constraint is only checked when required, i.e., when there is a change to `friends` or `enemies`.

### Uniqueness Constraints ###

In addition to JSR 303 validation and the `@Validate` annnotation, JSimpleDB also supports validation of uniqueness constraints on simple fields. These constraints verify that each object's value in some field is unique among all objects containing that field. Fields with uniqueness constraints must be indexed.

For example, this code would ensure all usernames were unique:

```java

@JSimpleClass
public class User implements JObject {

@JField(indexed = true, unique = true)
public abstract String getUsername();
public abstract void setUsername(String username);
}```

#### Excluded Values ####

Often there is a special value or two that you want to exclude from a uniqueness constraint, for example null values, or a value of zero. JSimpleDB gives you complete flexibility using the `uniqueExclude()` property.

For example:

```java

@JField(indexed = true, unique = true, uniqueExclude = { "NaN", "Infinity", "-Infinity" })
public abstract float getPriority();
public abstract void setPriority(float priority);```

## Spring Integration ##

JSimpleDB includes support for use with the <a href='http://projects.spring.io/spring-framework/'>Spring Framework</a>, including a <a href='http://docs.spring.io/spring/docs/current/javadoc-api/?org/springframework/transaction/PlatformTransactionManager.html'><code>PlatformTransactionManager</code></a> that integrates with Spring's <a href='http://docs.spring.io/spring/docs/current/javadoc-api/?org/springframework/transaction/annotation/Transactional.html'><code>@Transactional</code></a> annotation.

See the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/spring/package-summary.html'>API Javadocs</a> for more info.

## Command Line Interface ##

JSimpleDB includes a command line interface (CLI) program just like most databases do. However, JSimpleDB's CLI can parse Java expressions, allowing you to work in your data's natural language.

The JSimpleDB CLI has these features:
  * Database maintenance commands
    * Schema inspection and management
    * XML import/export
  * Include your own custom CLI commands, defined by annotated classes on the classpath
  * Command line history searching and editing (provided via <a href='https://github.com/jline/jline2'>JLine</a>)
  * Pervasive tab-completion, supported by all internal parsers
  * Java expression parser/evaluator
    * Use regular Java expressions for database queries and changes
    * Built-in parse customizations, such as object ID literals
    * Extended expression syntax with built-in functions such as `foreach()`, `concat()`, etc.
    * Include your own custom functions, defined by annotated classes on the classpath

Here's a few examples of using the CLI to query into the provided demo database (you can view the model classes <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/demo/package-summary.html'>here</a>).

First, we count how many planets there are and then show them sorted by mass:
<blockquote><pre>
Welcome to JSimpleDB. You are in JSimpleDB CLI Mode. Type `help' for help.<br>
<br>
JSimpleDB> import org.jsimpledb.demo.*              # works the same as Java's import statement<br>
JSimpleDB> eval count(all(Planet))                  # count how many planets there are<br>
9<br>
JSimpleDB> eval foreach(                            # show all planets sorted by mass<br>
->   concat(<br>
->     queryIndex(Planet, "mass", Float.class).asMap().values()),<br>
->     $planet,<br>
->     print(String.format("Name: %-10s Mass: %.0f", $planet.name, $planet.mass)))<br>
Name: Pluto      Mass: 13099999664401168000000<br>
Name: Mercury    Mass: 330000001687677400000000<br>
Name: Mars       Mass: 642000016384680500000000<br>
Name: Venus      Mass: 4869999810916809000000000<br>
Name: Earth      Mass: 5969999912619192000000000<br>
Name: Uranus     Mass: 86800001317335690000000000<br>
Name: Neptune    Mass: 101999998530235880000000000<br>
Name: Saturn     Mass: 568000005560064000000000000<br>
Name: Jupiter    Mass: 1897999959991329600000000000</pre></blockquote>

You can see the use of the built-in top-level functions `count()`, `all()`, `foreach()`, `concat()`, `queryIndex()`, and `print()`. You can add your own custom functions using <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/parse/fun/Function.html'><code>@Function</code></a>-annotated classes from the classpath.

Now let's set a few variables for some planets we are interested in:

<blockquote><pre>
JSimpleDB> eval $earth = filter(all(Planet), $x, $x.name.equals("Earth")).iterator().next()<br>
org.jsimpledb.demo.Planet$$JSimpleDB@364ee37d<br>
JSimpleDB> eval $earth.mass<br>
5.97E24<br>
JSimpleDB> eval $jupiter = filter(all(Planet), $x, $x.name.equals("Jupiter")).iterator().next()<br>
org.jsimpledb.demo.Planet$$JSimpleDB@4cd452db</pre></blockquote>

Note Java bean properties can be accessed using dot-property notation. Let's show Jupiter's moons:

<blockquote><pre>
JSimpleDB> eval $jupiter.satellites<br>
[org.jsimpledb.demo.Moon$$JSimpleDB@3963a1b8, org.jsimpledb.demo.Moon$$JSimpleDB@3c0e5477, ...</pre></blockquote>

Instead of their `toString()` values, let's show their names:

<blockquote><pre>
JSimpleDB> eval transform($jupiter.satellites, $x, $x.name)<br>
[Io, Europa, Ganymede, Callisto]</pre></blockquote>

The CLI extends regular Java operators with additional smarts. For example, the `&` operator, when applied to two `Set`s, will intersect them. For example, to find all moons of Jupiter above a certain mass:

<blockquote><pre>
JSimpleDB> eval $heavy_objects = filter(all(), $x, $x.mass >= 1e23)<br>
[org.jsimpledb.demo.Star$$JSimpleDB@4bad1871, org.jsimpledb.demo.Planet$$JSimpleDB@511fc987, ...<br>
JSimpleDB> eval transform($jupiter.satellites & $heavy_objects, $x, $x.name)<br>
[Ganymede, Callisto]</pre></blockquote>

Objects can be referred to using object ID literals, which have the form `@` followed by 16 hex digits:

<blockquote><pre>
JSimpleDB> eval $jupiter.objId<br>
fc21bf0000000005<br>
JSimpleDB> eval @fc21bf0000000005.name<br>
Jupiter</pre></blockquote>

## Vaadin GUI ##

JSimpleDB also includes a Vaadin-based graphical user interface (GUI). It provides a way to view and edit objects in a JSimpleDB database, as well as perform queries using the same Java syntax supported by the CLI.

The GUI is entirely auto-generated from your Java model classes.

## Core Database Layer ##

JSimpleDB is implemented in two distinct layers: the upper "JSimpleDB" layer, which is the layer you normally work with, and the "core API" layer, which is independent of the Java language. Think of the JSimpleDB layer as the "object" layer and the core API layer as the "data" layer. This separation is critical for maintaining rigorous data integrity in the database itself, while also allowing complete flexibility at the Java level.

This section gives an overview of the core API layer. Normally the core API can be ignored; instead, you should only ever need to think in Java. However, an understanding of the core API layer can elucidate how JSimpleDB works. See the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/package-summary.html'>core API Javadoc</a> for implementation details.

The core API layer has its own notion of schema, objects, data types, references, etc., which mostly models Java but has a few important differences. In particular, core API "objects" are really just "structures", and references and `enum` types are handled differently.

The core database layer has the following notions:
  * **Schema**
    * Has a **version number** (positive integer)
    * Includes a collection of **object types**
  * **Object Type**
    * Identified by a unique storage ID
    * Includes a collection of **fields**
  * **Objects**
    * Objects have an associated object type
    * Objects have a schema version number
  * **Fields**
    * Is either **simple**, **complex**, or **counter**
    * Has a unique storage ID
  * **Simple Fields**
    * Holds an atomic, sortable value like `int` or `Date`
    * Primitive types are supported
    * Non-primitive types may be null
    * May optionally be **indexed**.
  * **Reference Fields**
    * A special type of simple field that holds a reference to an object
    * Always indexed
  * **Complex Fields**
    * Sets, Lists, and Maps
    * Complex fields have one or more **sub-fields**, which are always simple fields
    * A complex field may not be itself indexed, but any of its simple sub-field(s) may be
    * Sub-fields may have primitive type; if so, nulls are not allowed and `IllegalArgumentException` is thrown if you try to add one
  * **Counter Fields**
    * Optimized for concurrent add/subtract updates
    * Does not support indexing or `@OnChange`

All objects and fields have a unique **storage ID**. However, the same field may be contained in multiple object types (this is how Java sub-types are handled).

Any storage ID that is used by two or more schema versions must be used consistently. This means the storage ID of an object type must always be associated with an object type, and the storage ID of a field must always be associated with a field having the same type (including sub-fields if complex).

There is one exception, however: simple fields may change whether they are indexed between schema versions. When a field's indexing is turned on or off between schemas, an object containing that field will appear in the index only when the schema associated with the object's version configures the field as being indexed.

JSimpleDB validates all of this for you, and will throw an exception if you try to do anything invalid.