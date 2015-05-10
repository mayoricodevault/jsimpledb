_(This page is under construction)_



## Architecture ##

#### Is JSimpleDB a database? ####

JSimpleDB is a _persistence layer_ that sits between your Java application and some other, underlying key/value database. The underlying database is responsible for providing transactions and durably storing information. JSimpleDB provides all of the remaining features you expect from a "database" and more, including indexes, a command line tool, auto-generated Vaadin GUI, etc.

With this design JSimpleDB can make persistence simple, natural, and completely type safe for a Java application, without sacrificing scalability or practical convenience.

Almost every database in existence is, at its heart, just some form of key/value store. JSimpleDB let's the database do what it's really good at - storing key/value pairs - and takes over from there with the goal of providing an optimal experience Java programmers.

#### What does the overall design of JSimpleDB look like? ####

JSimpleDB is has two layers:
  * The **core API** layer
  * The **Java model** (or **JSimpleDB**) layer

The JSimpleDB layer is what Java programmers normally deal with. The JSimpleDB layer relies on the core API layer to do most of the actual work.

**Core API Layer**

The core API layer provides concepts roughly analogous to the usual table, column, and row storage concepts, but instead calls those concepts **object type**, **field**, and **object**, respectively. However, this analogy is loose and there are some subtle but important differences. For example, two different object types can contain the same field. This allows you, for example, to index a field across multiple object types - even if neither of those object types is a superclass of the other. For example, you can index a field corresponding Java interface bean property.

All core API fields have a strictly well-defined type, sort ordering, and serialized encoding (see <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/FieldType.html'><code>FieldType</code></a>). The core API includes built-in support for a few special field types, including reference types (i.e., "pointers"), identifier list "enum" types, lock-free counter types. The core API also supports user-defined types.

In addition to the atomic **simple** field types just mentioned, the core API layer also provides support for list, set, and map **complex** field types. Indexes on both simple and complex fields are supported, and composite indexes on multiple simple fields are supported.

The set of all object types and their fields defines a **schema**. The core API allows multiple different schemas to exist at the same time in the same database; each schema has a unique integer **version**. As a consequence, all objects in the database are versioned. An object type may different fields in different schema versions.

All core API layer stored types (objects, fields, indexes, etc.) are identified by an integer **storage ID**, not by name. This allows names to change at a higher level without affecting the core API schema structure.

Although written in Java, there's nothing inherently Java specific about the core API layer. The "objects" in the core API layer are just data structures: there is no explicit notion of class, inheritance, or methods.

**JSimpleDB Layer**

The **JSimpleDB** layer sits on top of the core API layer. It provides the developer-friendly, Java-centric view of the core API layer. You normally only need to deal with the JSimpleDB layer.

At the JSimpleDB layer, the "schema" is defined by your **Java model classes**; these are identified by the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/JSimpleClass.html'><code>@JSimpleClass</code></a> annotation. To restate that: your set of Java model classes **is** your JSimpleDB schema; there is no separate schema "configuration" required. Under the covers of course, the JSimpleDB layer generates an appropriate core API schema from your model classes and provides this to the core API layer.

The JSimpleDB layer also does any necessary translation of core API values. For example, in the core API layer a "reference" is described by a 64-bit object identifier (see <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/ObjId.html'><code>ObjId</code></a>), whereas in the JSimpleDB layer a reference is a Java model object.

#### Why is the core API layer / JSimpleDB layer split important? ####

First, it allows complete flexibility in your Java model classes, while still providing well-defined semantics, strict type safety, and easy version migration, even in the face of arbitrary code refactoring (no small feat).

Secondly, sometimes you want to inspect or modify data directly, without any "object orientedness", i.e., without the possibility of any Java model class methods being invoked as listeners or whatever. The core API lets you do this.

The JSimpleDB command line interface (CLI) utility also supports this notion: it can run in either core API mode or JSimpleDB mode.

## Fields and Types ##

#### What simple types are supported? ####

JSimpleDB supports the following simple types out of the box:
  * Primitive types
  * Primitive wrapper types
  * References to Java model classes (or any wider type)
  * `Enum` types
  * Arrays of any simple type up to 255 dimensions (passed by value)
  * `java.lang.String`
  * `java.util.Date`
  * `java.util.UUID`
  * `java.util.File`
  * `java.util.regex.Pattern`

#### Can I create my own simple types? ####

Yes, by writing a class that subclasses <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/FieldType.html'><code>FieldType</code></a>.

An easy way to create a custom type for any type that can be encoded as a `String` is to pass an appropriate <a href='http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/base/Converter.html'><code>Converter&lt;T, String&gt;</code></a> to a new instance of <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/StringEncodedType.html'><code>StringEncodedType</code></a>.

You can also provide custom types annotated with <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/JFieldType.html'><code>@JFieldType</code></a> on the classpath.

#### How are `Enum` values stored? ####

In the core API layer, `Enum` values are represented by <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/EnumValue.html'><code>EnumValue</code></a> objects. In the JSimpleDB layer, they are represented by instances of the appropriate `Enum` Java model class.

At the core layer, two enum types are considered equivalent if and only if they have the same (ordered) identifier list. This means you can move an `Enum` model class to a different package without requiring a schema change. However, if you add or change an `Enum` value, that forces a schema change.

#### What collection types are supported? ####

Lists, Sets, and Maps.

The element, key, and value can have any simple type. In the case of primitive types, null values will be disallowed.

Lists have performance characteristics similar to `ArrayList`.

## Querying Data ##

#### Does JSimpleDB have a query language? ####

No. All queries are done using normal Java.

#### Do I need a DAO layer? ####

No.

For a few operations such as creating a new instance and querying an index, you invoke methods on the current <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JTransaction.html'><code>JTransaction</code></a>.

Everything else can be normal Java, and all access methods can be either instance or static methods in your Java model classes.

Let's take a simple example Java model with `Account` and `User` model classes. We have these requirements:
  * Every user must have an account
  * Usernames must be unique
  * We can efficiently find users by username
  * We can efficiently find all users associated with an account

Here's an what those classes might look like, including all the "DAO" methods you would need:
```java

@JSimpleClass
public abstract class User implements JObject {

// Fields

// Get this user's username
@JField(indexed = true, unique = true)
@NotNull
public abstract String getUsername();
public abstract void setUsername(String username);

// Get this user's account
@NotNull
public abstract Account getAccount();
public abstract void setAccount(Account account);

// "DAO" methods

// Create new user
public static User create() {
return JTransaction.getCurrent().create(User.class);
}

// Find user by username
public static User getByUsername(String username) {
final NavigableSet<User> users = JTransaction.getCurrent().queryIndex(
String.class, "username", User.class).get(username);
return users != null ? users.iterator().next() : null;
}
}

@JSimpleClass
public abstract class Account implements JObject {

// Fields

// Get the name of this account
@NotNull
public abstract String getName();
public abstract void setName(String name);

// "DAO" methods

// Create new account
public static Account create() {
return JTransaction.getCurrent().create(Account.class);
}

// Get all users associated with this account
public NavigableSet<User> getUsers() {
final NavigableSet<User> users = this.getTransaction().queryIndex(
User.class, "account", Account.class).asMap().get(this);
return users != null ? users : NavigableSets.<User>empty();
}

// Get all accounts
public static NavigableSet<Account> getAll() {
return JTransaction.getCurrent().getAll(Account.class);
}
}```

Congratulations, you're done! You've just configured an entire Java application persistence layer.

#### How do I do aggregate queries like `AVG()` and `SUM()` and things like `GROUP BY`? ####

You write them yourself in Java.

#### Isn't that inconvenient? ####

Yes and no.

JSimpleDB believes that having everything done in maintainable Java code is worth the trade-off of having to write a few helper methods. Code is only written once, but it's maintained forever.

Also, and perhaps more importantly, JSimpleDB makes it impossible to write a poorly performing query unless you explicitly write it that way yourself.

For example, in SQL a query like `SELECT * FROM USER WHERE LOWER(USERNAME) = 'fred'` will require examining every row of the `USER` table _even if the USERNAME column is indexed_, because of the use of `LOWER()` in the `WHERE` clause.

The problem is that it's not obvious that this query is going to be slow just by looking at it. Of course this is just a simple example, in the real world query performance can be much more obfuscated.

In JSimpleDB, to implement that query, you'd have to write a loop that iterates over every `User` in the database. This makes the performance reality obvious.

The more efficient thing to do would be to add an indexed derived field containing the lower case username, and an <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnChange.html'><code>@OnChange</code></a> method to keep it synchronized automatically:

```java

@JSimpleClass
public abstract class User implements JObject {

// Fields

// Get this user's username
@JField(indexed = true, unique = true)
@NotNull
public abstract String getUsername();
public abstract void setUsername(String username);

// Derived fields

// Get this user's lower case username - automatically kept in sync
@JField(indexed = true)
public abstract String getLowercaseUsername();
protected abstract void setLowercaseUsername(String username);   // note: not public

@OnChange("username")
private void onUsernameChange(SimpleFieldChange<User, String> change) {
final String username = change.getNewValue();
this.setLowercaseUsername(username != null ? username.toLowerCase() : null);
}

// "DAO" methods

// Find users by lowercase username
public static NavigableSet<User> getByLowercaseUsername(String lowercaseUsername) {
return JTransaction.getCurrent().queryIndex(
String.class, "lowercaseUsername", User.class).get(lowercaseUsername);
}
}```

#### Can I query by any Java type? What about interface types? ####

Yes and yes.

#### How do I do database joins? ####

Instead of thinking in terms dictated by the database technology, JSimpleDB lets you think in more natural terms of sets, specifically <a href='https://docs.oracle.com/javase/7/docs/api/java/util/NavigableSet.html'><code>NavigableSet</code></a>s, which provide efficient range queries, reverse ordering, etc.

JSimpleDB also provides efficient union, intersection, and difference implementations (see <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/util/NavigableSets.html'><code>NavigableSets</code></a>). These operations provide the functionality of database joins.

## Indexes ##

#### How do you query an index? ####

Using <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JTransaction.html'><code>JTransaction.queryIndex()</code></a>.

Index queries are parameterized by the Java types you are interested in and type safe.

#### What happens if I make a schema change that simply adds or removes an index on a field? ####

JSimpleDB supports schema changes that add or remove indexes. If you do this, only objects whose schema versions have the field indexed will be found in the index.

## Key/Value Stores ##

#### What requirements must the key/value store satisfy? ####

The key/value store must satisfy these criteria:
  * Provides consistent, ACID-compliant transactions
  * Supports data access via the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/KVStore.html'><code>KVStore</code></a> interface:
    * Efficiently get, put, and remove keys
    * Efficiently find the next higher or lower key
    * Efficiently iterate a consecutive range keys in forward or reverse direction
    * Efficiently remove a contiguous range of keys

See the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/KVDatabase.html'><code>KVDatabase</code></a> Javadoc for details.

#### What key/value databases are supported? ####

Currently the following key/value stores are supported:
  * Any [SQL database](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/sql/package-summary.html)
  * [FoundationDB](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/fdb/FoundationKVDatabase.html)
  * [Oracle's Berkeley DB Java Edition](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/bdb/BerkeleyKVDatabase.html)
  * [LevelDB](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/leveldb/LevelDBKVDatabase.html)
  * For testing, prototyping, and/or non durable usage:
    * [In-memory database](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/simple/SimpleKVDatabase.html)
    * [XML flat file database](http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/kv/simple/XMLKVDatabase.html)

Several other popular NoSQL databases are not compatible because of one or more of the following:
  * Keys are not sorted (instead, only hashed)
  * Keys have limited length (e.g., at most 64 or 128 bits)
  * ACID semantics not provided

#### Why does JSimpleDB require ACID semantics? ####

The philosophy behind JSimpleDB states that simplicity promotes solid, reliable, maintainable code. In particular, if the code is too complicated, it becomes unfeasible for developers to prove to themselves that the code is fully correct -- and of course if the developers can't ensure the code is fully correct, it won't magically become fully correct by itself. Stated another way, "complexity kills".

A persistence technology that doesn't provide consistent, ACID-compliant transactions is just too difficult for programmers to reason about. In addition, recently there has been a change in the traditional belief that you can't have both ACID compliance and scalability: Google's Spanner database and FoundationDB are [proving this assumption wrong](http://highscalability.com/blog/2012/9/24/google-spanners-most-surprising-revelation-nosql-is-out-and.html) (see also [this](http://blog.foundationdb.com/7-things-that-make-google-f1-and-the-foundationdb-sql-layer-so-strikingly-similar) and [this](http://blog.foundationdb.com/databases-at-14.4mhz)).

## Data Storage and Layout ##

#### How does JSimpleDB encode information as keys and values? ####

See [LAYOUT.txt](http://jsimpledb.googlecode.com/svn/trunk/LAYOUT.txt) for a basic overview.

## Configuration ##

TODO

## Schemas and Versioning ##

#### Do schema changes affect the whole database? ####

No. JSimpleDB is designed to avoid any "whole database" operations that might limit scalability.

Schema changes are applied on demand, on a per-object basis as objects are encountered during normal operation.

#### What happens if my Java model classes change? Won't that break the mapping to the core API objects and fields? ####

The short answer is: JSimpleDB always guarantees Java type safety, even in the face of arbitrary Java model class refactoring.

JSimpleDB allow arbitrary code refactoring at the Java model layer, but if the generated core API schema changes in a structurally incompatible way due to some change in your model classes, JSimpleDB requires you to declare a new schema (by specifying a new schema version number).

If you try to use an incompatible schema, you'll get a <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/SchemaMismatchException.html'><code>SchemaMismatchException</code></a> when trying to open a new transaction.

#### How does JSimpleDB know that some older version of my code had a different schema? ####

When you run code with a new schema version for the first time, JSimpleDB records the schema in the database. From that point onward, JSimpleDB will not allow the use of any other, incompatible schema with that same version number.

#### What happens to objects created by an older schema version after an upgrade to a newer schema version? ####

After a schema change, your new code will create objects with the new schema version. Objects created by your old code will continue to exist in the database unchanged.

#### What happens when a new version of my code tries to read an object created by an old version of my code? ####

When your new code first encounters an object with an older version number, the object will be automatically upgraded to the new schema version. Newly added fields will be initialized to their default values, and removed fields will be deleted.

If that's good enough for you, you don't need to do anything else.

However, JSimpleDB also gives you an opportunity to perform schema change "fixups" if necessary, by invoking any <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a> methods on the object. All of the fields in the old version of the object (including fields about to be removed) are made available to this method.

#### What happens when an old version of my code tries to read an object created by a new version of my code? ####

Same thing. JSimpleDB doesn't really care about the schema version numbers themselves; they are simply unique identifiers. So "upgrades" and "downgrades" are handled exactly the same way.

If you will have multiple versions of your code writing to the same database, then both versions will need to know how to handle an object version change from the other version. In this situation a phased upgrade process is recommended:
  * Upgrade nodes to understand both the old and new schema versions, but continue using the old schema
  * Upgrade nodes to start using using the new schema
  * (Optional) Force upgrade all remaining database objects, e.g., use CLI command: `eval foreach(all(), $x, $x.upgrade())`
  * (Optional) Garbage collect the old schema version from your database meta-data, e.g., use CLI command: `delete-schema-version 3`
  * (Optional) Remove support for the old schema version in your code

This process allows for rolling schema upgrades across multiple nodes with no downtime.

#### How will newer versions of my code know how to properly decode objects stored by older versions? ####

The core API layer records all of the schemas ever used in a database in the database meta-data, so it always knows how to decode any object.

#### What happens if a newer schema version removes a Java model class? How can I access those objects? ####

Objects created by older schema versions whose model class no longer exists will have type <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/UntypedJObject.html'><code>UntypedJObject</code></a>. If needed, you can access their fields using the field introspection methods of the <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JTransaction.html'><code>JTransaction</code></a> class. Typically, however, deleting a Java model class means you don't need or want the data anymore.

You can encounter <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/UntypedJObject.html'><code>UntypedJObject</code></a> instances in the following two situations:
  * As the value of a removed field in an <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a> schema update callback method when:
    * The older version contained the model class; and
    * The newer version does not
  * In index query results, when:
    * The older version contained the indexed field in a model class that was removed; and
    * <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/UntypedJObject.html'><code>UntypedJObject</code></a> is assignable to the Java type requested by the query (e.g., you request all objects in the index of type `Object`).

Note that type safety is still preserved in all situations.

#### What if a new schema changes an object reference to have a narrower Java type? Won't then older versions of the class violate type safety? ####

No, because during a schema upgrade JSimpleDB automatically eliminates any references that would no longer be valid due to narrowing Java types.

Of course, you have an opportunity to do something with the old, invalid references in your <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a> method.

#### What happens if I change a `float` field to `String`, etc.? ####

JSimpleDB requires a certain amount of consistency between schema versions. In particular, a field cannot have two different types between schema versions. This restriction makes it possible for an index on a field to contain objects from multiple different schema versions.

If you need to change a field's type, instead just add a new field with the new type and remove the old one with the old type, and do the appropriate type transformation in an <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a> method.

#### How would I handle a schema change that splits a class `Vehicle` into `Car` and `Truck`? Or that does the reverse? ####

These types of schema changes are tricky for any Java persistence framework. For example, there's no way to avoid visiting every `Vehicle` at some point to decide whether it needs to be a `Car` or a `Truck`.

The easiest way to handle this scenario is to upgrade in two steps. In the first phase, all three classes exist (in the obvious inheritance arrangement), and your code knows how to handle all three. During this phase, a custom background upgrade thread iterates through every instance, deciding what to do with it, updating or replacing it as necessary. In the second phase, all objects have been transitioned to the new classes, so the old class(es) are no longer needed and can be removed.

#### What if my schema change requires replacing instances of one class with instances of a different class? How do I update incoming references? ####

In JSimpleDB all reference fields are indexed, so you can simply query the index for each reference field that refers to the instance you are replacing, and then update those references.

#### What if model class A contains a reference to model class B, and then a schema change deletes class B? ####

Then the Java type of the reference in class A will also have to change, otherwise your code won't compile, or schema generation will fail because class B isn't a model class.

#### When and how do objects get upgraded to a newer schema version? ####

Objects are upgraded automatically the first time your code attempts to read or write a field in the object.

#### Is there a way to forcibly upgrade objects to the current schema version? ####

Yes, <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JObject.html'><code>JObject.upgrade()</code></a> upgrades an object to the current schema version.

From the CLI, you can upgrade every object by invoking `eval foreach(all(), $x, $x.upgrade())`.

#### How exactly does JSimpleDB prevent me from reading or writing a field incompatibly? ####

Fields are expliclitly typed; each type has an associated <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/FieldType.html'><code>FieldType</code></a> implementation.

#### Will my database get cluttered up with old schema versions from years gone by? ####

You can use the CLI command `delete-schema-version` to remove a recorded schema version from the database.

This operation will fail if any objects with that version still exist - you must upgrade (or delete) them first, e.g., using the CLI command `eval foreach(all(), $x, $x.upgrade())`

For simplicity, it is recommended to always upgrade objects after a schema change, so your <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a> methods only have to deal with one version change at a time.

#### How can I tell what schema versions are in use by objects in my database? ####

JSimpleDB keeps an internal index on object versions. Therefore, it's easy to query for which objects of which types have which versions.

For example, in the CLI to find how many objects of type `Vehicle` have version four, you would say `eval count(all(Vehicle) & queryVersion().get(4))`.

#### I changed my model classes and now new transactions are failing with <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/core/SchemaMismatchException.html'><code>SchemaMismatchException</code></a>... now what do I do? ####

Simply declare a new schema version and add <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/annotation/OnVersionChange.html'><code>@OnVersionChange</code></a> methods as necessary to handle any required schema change fixups.

#### How do I "declare a new schema version"? ####

The schema version must be provided explicitly when you configure a <a href='http://jsimpledb.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/jsimpledb/JSimpleDB.html'><code>JSimpleDB</code></a> instance.

In addition, you need to give JSimpleDB permission to record a new schema version in the database (this is just an extra safety check). The `allowNewSchema` boolean parameter is supplied each time you create a new JSimpleDB transaction.
