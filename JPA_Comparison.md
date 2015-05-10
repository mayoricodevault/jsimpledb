# Feature Comparison #

Here's a quick comparison between JSimpleDB and JPA, the most common Java persistence technology:

| **Feature/Issue** | **JPA** | **JSimpleDB** |
|:------------------|:--------|:--------------|
| Maturity level | Mature; since 2006 | New; since 2014 |
| Underlying database | SQL only | Any key/value store (including SQL) |
| Query language | JPQL, SQL, Criteria | None; use regular Java |
| Compile-time type safety | Only with Criteria | Always |
| Configuration | Java Annotations + XML | Java Annotations |
| Number of annotation classes | 89 | 12 |
| Simple data types | SQL data types | Primitives, Date, etc. + any user-definable |
| Array types | `byte[]` only | Any type including multi-dimensional |
| Indexable data types | SQL data types | Any type |
| Composite indexes | Supported | Supported |
| Collection types | Sets, lists, maps | Sets, lists, maps |
| Lockless counter type | Not supported | Supported (if key/value does) |
| Snapshot Transactions | Partial/implicit | Supported |
| Query indexes in snapshots | Not supported | Supported |
| Field change notifications | Simple types only | Supported |
| Notification via reference path | Not supported | Supported |
| Notification with old/new value | Not supported | Supported |
| Slow query debug | Difficult | Easy |
| Reference inversion | Implicit; via query | Supported |
| Versioned objects | Not supported | Supported |
| Version updates provide old/new fields | Not supported | Supported |
| Rolling online schema changes | Not supported | Supported |
| Spring integration | Supported | Supported |
| Delete actions | Supported via DDL | Supported |
| XML import/export objects | Not supported | Supported |
| Command-line client | Only via SQL database | Supported |
| CLI parses Java expressions | Not supported | Supported |
| Vaadin GUI auto-generator | Not supported | Supported |

# Problems & Solutions #

If your application is written in Java, and you need to persist data onto disk, then there are certain issues and challenges that will always be present no matter what persistence layer you use. These issues are inherent in the problem that all of the persistence layers are trying to solve.

So it's instructive to compare persistence layers by looking at these inherent problems and how the various layers address them.

Note that JPA does a great job solving the problem it's designed to solve: allow Java object-oriented access to an underlying SQL database. However, what's not always obvious until you are deep in the weeds trying to debug some hairy problem are the sacrifices JPA makes because it is based on SQL. When you free a persistence layer from that requirement, as JSimpleDB is, the experience at the Java level can be greatly simplified and improved at the same time.

## Problem #1: Transactions ##

Transactions allow developers to think rationally about how their code is functioning and most persistence layers support some transactional notion. From a Java programming point of view however, other than knowing they exists there is often minimal need to directly interact with transactions.

Typically, transaction management is handled by a separate layer (e.g., Spring) and transactions are exposed to the application through an implicit association of a transaction with the current thread. Commit errors convert into exceptions, and conversely exceptions within a transaction (ususally) result in an automatic transaction rollback.

This is an area where both JPA and JSimpleDB take the same approach; there is little overall difference between the two persistence layers with respect to transaction handling.

## Problem #2: Data Access ##

Once a transaction is open, there is the basic inherent problem of how do you actually access the data in the database?

Both JPA and JSimpleDB provide access through plain Java objects (POJO's) that are either obtained from the transaction itself through some kind of query, or re-attached from a previous transaction. However, there are some important differences as well.

### Transaction Data Access ###

With JPA, each transaction generates a distinct POJO, even for the same database identity; if you want to re-use a POJO in a new transaction, you have to explicitly reattach (or merge) it in, and get back the new POJO to use. In JSimpleDB, the same actual POJO Java instance is always used to represent the same database identity; no merge is needed. This (non-snapshot) POJO will always draw its state from the transaction associated with the current thread.

### Post-Transaction Data Access ###

Typically transactions need to be short-lived to reduce contention. That means your application is going to copy some data out of the transaction into memory for use later after the transaction closes. So the next inherent problem is how do you copy that data out and how do you access it once the transaction closes?

For Java applications, it's very convenient if this post-transaction access can use the same POJO-based Java API as normal transaction access. Indeed, both JPA and JSimpleDB have this feature, but take different approaches.

It's worth noting that whether using JPA or JSimpleDB, you have to know ahead of time to read information into memory during the transaction if you want to access it after the transaction ends.

With JPA, POJO's retain some of their state after a transaction ends, becoming "detached" objects. However, exactly which state is retained is not always clear to the programmer, as it depends for example on whether queries were performed with lazy loading or not and which relationships the application accessed during the transaction. In other words, the JPA POJO's just represent a cache, and whatever happens to be in the cache when the transaction ends is what's available.

There are several downsides with this approach. First, JPA's cache is always separate from, and in addition to, whatever cache is provided by the database itself. This means that if the database is also caching data in memory during a transaction, that cache is completely redundant and wasting memory.

Another downside is that the set of data you need to access after a transaction ends is not necessarily equal to the set of data that you happened to access and bring into the cache during the transaction (though the former should always be a subset of the latter). However, JPA equates these notions, causing potentially wasted memory. For example, if during a transaction you access an object and iterate through some large collection property (perhaps to calculate some aggregate value), that collection will be cached in memory after the transaction as long as you keep a reference to the object, even if all you actually needed after the transaction was a few simple properties of the object.

A third downside to the JPA approach is that the post-transaction state available from detached JPA POJO's is impossible to query against. In other words, there is no way to perform the normal JPA queries (SQL, JPQL, Criteria) outside of a transaction.

In the JSimpleDB approach, transaction POJO's don't retain any state when the transaction closes; trying to access them results in an exception. In fact, JSimpleDB doesn't cache any data: JSimpleDB POJO's represent a direct view of the data in the database as seen through the current transaction. If for some reason the caching already provided by the transaction itself is not sufficient, this can be easily (and more appropriately) handled in a separate key/value caching layer.

JSimpleDB does however provide explicit post-transaction data access support, using "snapshot" transactions. These are lightweight, in-memory transactions that start out completely empty. Any data that you need to access after the transaction closes you "copy out" of the real transaction into a snapshot transaction. JSimpleDB provides methods to make this operation easy, for anything from a single object to an arbitrary graph of objects (with potentially circular references) that you define. This way, the data available in the snapshot transaction (and taking up memory) is always exactly what you specified that you needed, no more no less.

In addition, snapshot transactions have all of the functionality of normal transactions; you can query indexes, get notified of changes, etc. Querying for data in a snapshot transaction works exactly the same way as querying for data in a "real" transaction. The only thing you can't do with a snapshot transaction is `commit()` it. As a side note, snapshot transations generate their own distinct POJO's: whereas normal transactions share the same POJO representatives, JSimpleDB provides a distinct "snapshot" POJO for each snapshot transaction; each snapshot POJO in turn "knows" which snapshot transaction it is associated with.

In addition, if necessary you can create multiple snapshot transactions, and they will persist for as long as you hold a refernence to them. The support for copying objects between a "real" transaction and snapshot transaction works in general between any two transactions, whether real or snapshot.

## Problem #3: Relationships and Collections ##

All databases must support some notion of relationships, or "pointers", between database records. Pointers in some form are required to represent one-to-one, one-to-many, many-to-one, and many-to-many relationships. For example, if a database stores readings for water meters, there must be some pointer that links a meter reading to the associated water meter, and in this case the relationship is many-to-one, because a single meter can have multiple readings.

JPA and JSimpleDB represent relationships and collections to the programmer in a similar way, though with important differences. Both use normal Java object references to represent relationships, and normal Java `Set`, `List`, and `Map` interfaces to represent collections. These references and collection classes are meant to be Java reflections of the underlying database reality.

However, the JPA representation, as a reflection of the underlying database reality, is both less precise and less accurate than JSimpleDB's.

### Settable Collection Fields ###

JPA allows a programmer to set a collection field to `null`, e.g., `meter.setReadings(null)`, but this makes no sense - even though the relationship can sometimes be empty, it always exists. In addition, in newly created objects the programmer must remember to initialize the field (whereas in objects returned by queries, JPA will populate the field automatically).

These problems simply don't exist with JSimpleDB, because collection fields have only getter methods, are declared as `abstract` methods, and JSimpleDB guarantees that the returned value is never null.

### The "inverse" problem ###

For collection relationships between entities, JPA defines a notion of the "forward" direction and the "inverse" direction. For example, you might be able to access both `parent.getChildren()` and `child.getParent()`. In this case `child -> parent` is the forward direction and `parent -> child` is the "inverse" direction. What this really means is that the `parent -> child` relationship represented by `parent.getChildren()` is a phantom that does not necessarily represent reality. In the underlying database, there is only one pointer - from child to parent, but JPA presents two versions of it.

Only one can be right, so it begs the question: what happens when you say both `parent1.getChildren().add(child)` and `child.setParent(parent2)` where `parent1 != parent2`? (Answer: `parent2` becomes the parent.) This creates an opportunity for hard-to-find bugs when application code somewhere forgets to update both sides of the relationship.

JSimpleDB solves this problem by not creating two representations of the same relationship in the first place. With JSimpleDB only the `child -> parent` database relationship (the "real" one) is defined. To access the "inverse" direction from `parent -> child`, you query the index associated with that relationship:

```java

// class Child

public abstract Parent getParent();
public abstract void setParent(Parent parent);

// class Parent

public NavigableSet<Child> getChildren() {
final NavigableSet<Person> kids = this.getTransaction().queryIndex(
Child.class, "parent", Parent.class).asMap().get(this);
return kids != null ? kids : NavigableSets.empty();
}```

What gets returned from `parent.getChildren()` is not a copy or snapshot of the relationship, it is a real-time view into the relationship. Because `child.getParent()` and `parent.getChildren()` are accessing the same data, it's not possible for them to get out of sync. Invoking `child.setParent(parent1)` always means that `parent1.getChildren().contains(child)` becomes immediately true.

### Collections and Sorting ###

JPA sets and maps normally implement the `Set` and `Map` interfaces. You can improve this to `SortedSet` and `SortedMap` with the right magic annotations and caveats, such as having to ensure your Java sort order matches your database sort order.

However the semantics that databases actually provide are those of the more powerful `NavigableSet` and `NavgiableMap` interfaces. For example, these allow you to view the collection in reverse order.

JSimpleDB sets and maps implement `NavigableSet` and `NavgiableMap`, and the JSimpleDB type system guarantees that the Java ordering (which is defined by the type) always matches the database ordering.

## Problem #4: Indexes ##

Fundamental to any database is support for indexing. Indexing is simply the automated creation of derived information that makes it efficient to perform certain queries that would otherwise be too slow. More precisely, whereas objects represent a mapping from object ID to property values, an index is a (sorted) mapping from property value(s) back to object ID.

The Java language itself does not have any built-in notion of an "index". In regular Java, if for example you want to be able to efficiently search for `Person` objects by name, you will have to programmatically create and maintain an index on the `name` property yourself using `TreeMap` or whatever. In other words, you have to homebrew your own index. Since indexing is core to the function of databases, this leaves the question of how indexes should be exposed to Java.

JPA does not directly provide access to indexes; neither does SQL itself. Instead, it leaves it up to you to ensure that that whatever queries you are performing are going to be efficient. This of course depends not only on the queries themselves and what indexes are defined on the database, but also on how JPA translates your query into SQL, and how the database maps that SQL into a query plan. The many layers involed can make this simple determination surprisingly difficult.

In contrast, JSimpleDB provides a direct Java-level view into your defined indexes using the `NavigableMap` interface. As a result, JSimpleDB doesn't have or need a "query language". All queries are performed using normal Java, and their efficiency (or lack thereof) will be obvious when looking at the code.

In addition, there is less need for composite (multi-field) indexes. In JSimpleDB, every index resolves to a `NavigableSet` of objects, and these sets can always be efficiently unioned and intersected using the methods in the `NavigableSets` utility class. For example:

```java

// Person fields

public abstract String getLastName();
public abstract void setLastName(String lastName);

public abstract String getFirstName();
public abstract void setFirstName(String firstName);

// Person index queries

public static NavigableMap<String, NavigableSet<Person>> queryLastName() {
return JTransaction.getCurrent().queryIndex(Person.class, "lastName", String.class).asMap();
}

public static NavigableMap<String, NavigableSet<Person>> queryFirstName() {
return JTransaction.getCurrent().queryIndex(Person.class, "firstName", String.class).asMap();
}

public static NavigableSet<Person> getByLastAndFirstName(String lastName, String firsName) {
final NavigableSet<Person> byLastName = Person.queryLastName().get(lastName);
final NavigableSet<Person> byFirstName = Person.queryFirstName().get(firstName);
if (byLastName == null || byFirstName == null)
return NavigableSets.empty();
return NavigableSets.intersection(byLastName, byFirstName);
}```

Composite indexes are therefore only needed when you require efficient queries to objects _sorted on multiple keys_.

## Problem #5: Database Types ##

The first inherent problem with persisting Java objects in any database is the potential for mismatch between Java types and the database's supported types. For example, you can map a `java.util.Date` property to a database `TIMESTAMP` column, but those types are not the same; e.g., MySQL does not support `TIMESTAMP` value prior to `1970-01-01 00:00:01`. You can map your object's `String` properties to `VARCHAR()` columns, but in MySQL the total length of those strings will be limited to 65,535 bytes. This kind of type mismatch creates lots of opportunities for subtle bugs.

Another problem is that databases usually restrict indexing to their supported types. If your application create custom types that don't map to supported types, you can't index them. For example, suppose you have a custom type for software versions with values such as `7.5alpha` and `10.3`, and you want those two versions to sort in that order. With SQL, neither a numeric nor a character column type will work. You can define your own custom binary type, but then you lose visibility into that type at the SQL level.

JPA has no solution to the type mismatch problem because it only works with SQL databases and maps Java properties directly to SQL columns.

JSimpleDB supports Java types precisely and sorts them naturally. It requires that all types have a well defined range of values, sort order, and equality semantics that behave identically both for Java instances and database-encoded values. JSimpleDB allows you to define your own custom types; these are first-class types that may be indexed, form collections, etc.

## Problem #6: Database Schema and Upgrades ##

A more challenging inherent problem with database persistence, no matter what language the application is written in, is that application code and the structure of the data it persists changes and evolves over time. The challenge is how to safely manage changes to the structure of data when the application is upgraded.

Both JPA and JSimpleDB are "schemaful" persistence layers. However, JPA provides no support for object versioning or schema migration, so with JPA you are on your own here. In other words, for this particular inherent problem, JPA provides no help whatsoever. Unfortunately, developers often only become aware of the complexity inherent in this problem after they have deployed code into production and are starting to contemplate how they are going to safely and reliably upgrade existing customers. The problem is exacerbated when the application is deployed across a cluster of servers and there is a requirement for zero downtime during the upgrade: then you have a situation where different servers will be running different versions of the code, with correspondingly different expectations of data structure, yet are talking to a "schemaful" database that can only have one schema at a time.

Some database technologies claim to solve this problem by being "schemaless". Of course, there is no such thing as "schemaless" data, there is only the question of which software layer enforces the schema. A database not having a schema is a "solution" to the above problem only in the sense that it now makes it entirely impossible for the database to help you solve a problem that "schemaful" databases in theory could, but in practice don't, solve.

Instead of going backward into schemaless anarchy, the more correct fix for the overall situation is to add explicit support for object versioning and schema migration into the database. That is, put the schema migration support at the same layer that defines and enforces the schema.

JSimpleDB does exactly this, and it does it (as always) in a Java-centric way. All objects are versioned, and you are allowed to change your object hierarchy any way you want. When an new application version encounters an object with an older schema version, the object is notified via any `@OnVersionChange`-annotated methods with all of the information it needs to migrate the object. The rest of the application need not be aware that any schema change has occurred. Object are also indexed by version, which facilitates schema migration by, for example, making it easy to write an object version migration background thread.

## Problem #7: Command Line Access ##

The next inherent problem with Java and persistence is that Java is not the most convenient tool for various administration tasks relating to databases. Some kind of command line tool is needed that allows _ad hoc_ data manipulation, queries, dump/restore, etc.

JPA's solution to this problem is an appropriate one, which is to rely on the underlying SQL database providing sufficient command line tools. However this approach as a major drawback: these tools know nothing about Java. You've left the Java world. To take a trivial example, you couldn't easily write an SQL statement that find all `Person` objects whose `name` has a certain Java `hashCode()`. For a more general example, you may have annotated your POJO's with lots of utility methods (e.g., `calculateLastPayment()`) that are entirely off-limits fromt the command line.

In any case, for JSimpleDB relying on database vendor tools is especially unattractive because unlike with JPA, the underlying database technology (i.e., key/value store) is untyped. So using the database vendor tools would mean you are trying to make sense of unintelligible binary blobs.

Instead, JSimpleDB provides its own command line interface (CLI) utility. JSimpleDB's CLI provides the usual maintenance capabilities, such as schema management, XML file import/export, etc. The CLI is not only written in Java, but it uses Java as its input and expression language. For example, the aforementioned `hashCode()` query would look something like this on the command line:
```
JSimpleDB> eval filter(all(Person), $x, $x.name.hashCode() == 1234)
```
It can evaluate arbitrary Java expressions, including those that invoke your own custom POJO Java methods.

In addition, you can also extend the functionality of the command line tool with your own Java-defined commands and functions, even further aligning the CLI utility with your Java application. Conversely, all of the CLI functionality can be included in your own application if desired.

JSimpleDB also makes it easy to embed the CLI into your own application. For example, you can make it accessible via `telnet`.

## Problem #8: GUI Tools ##

In addition to command line tools, for most databases there is some graphical user interface (GUI) tool as well. These tools are more or less the GUI equivalent of the corresponding CLI tools. Therefore, they have the same advantages and disadvantages. In particular, like the CLI tools, they are also detached from the world of Java.

JSimpleDB provides a Vaadin GUI tool for visually interacting with a JSimpleDB database. The GUI tool includes the same Java expression parser and pre-defined functions as the CLI tool. Like the CLI tool, you can incorporate it into your own application.

The GUI tool presents even better opportunities to take advantage of Java integration than the CLI tool. For example, your database access methods can be annotated with [`@FieldBuilder`](http://dellroad-stuff.googlecode.com/svn/trunk/publish/reports/javadoc/index.html?org/dellroad/stuff/vaadin7/FieldBuilder.html) annotations that allow you to define your own Vaadin widget for editing object properties.