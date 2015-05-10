## Java & Databases: A Love/Hate Relationship ##

Databases were created long before Java. They serve their function well.

However, using databases with Java has never been easy. For example, there is the well-known [object/relational impedance mismatch](http://en.wikipedia.org/wiki/Object-relational_impedance_mismatch) problem.

E.g., if a parent gets a new child, am I supposed to invoke
```
child.setParent(parent)
```
or
```
parent.getChildren().add(child)
```
...or both?

And why are some fields of an object available after the transaction closes but not others? How can I know which ones?

There are also lots of other issues, some more subtle than others. For example:
  * Databases have a limited range of column types that don't exactly match normal and/or custom Java types.
  * Lack of visibility into how Java queries are translated into actual database queries:
    * You can't tell if a database query will be fast or slow just by looking at it; you have to know how your ORM layer converts it into SQL, which columns are indexed, how smart the query optimizer is, and whether the database keeps a "secret" index for any aggregate functions.
    * You can't tell whether a database query is going to introduce locking problems (e.g., inter-transaction deadlocks caused by reversed locking order) by looking at it, because without (a) deducing how your ORM layer is going to convert your query into SQL, and (b) examining the database's query plan for that SQL, you don't know what order tables and rows are going to be accessed.
  * Database queries just look ugly nested inside Java code, even with recent improvements such as JPA criteria queries. There's no way to make them look "natural".
  * ORM layers often have murky areas of unspecified or unpredictable behavior, largely due to their inherent complexity. Instead, there should be no ambiguity what is going to happen in any particular situation.
  * ORM layers attempt to hide the complexity of converting everything into SQL, but they do so incompletely and imperfectly, resulting in a layer of abstraction for which you still must expend mental energy understanding and paying attention to lower level details
  * Lots of subtle violations of Java type-safety, e.g., when handling `Enum` types

Moreover, Java applications have a few common requirements that normal databases don't provide support for, such as: how do you update your database schema without bringing down every node while you run `ALTER TABLE FOO` statements?

Many of these problems have solutions, but traditionally they have necessarily been "roll your own" solutions.

A Java-centric persistence layer should make these problems straightforward to solve, in a Java-centric way.

## JSimpleDB Goals ##

The goal of JSimpleDB is to take what's good about databases, and what's good about programming in Java, and bring the two closer together.

At it's core, any database is just a bunch of functionality wrapped around a core sorted key/value technology of some kind. At its heart, the database can efficiently find, add and remove keys, and iterate over keys in order. On top of this is added indexing, data types, table and column structures, foreign keys, joins, and SQL.

However, that "wrapped functionality" has never been optimized for Java programmers, and in fact often serves to create additional obstacles.

JSimpleDB's attitude toward databases is, "You do the key/value store part, I'll do the rest".

JSimpleDB's Java-centric persistence model is designed to:
  * Be simple to understand for Java programmers
    * Everything should be configured using annotations
    * Everything should be done using normal Java objects
    * **Type Safety** is paramount
  * Be capable of scaling to large data sets and multiple nodes
    * JSimpleDB should be able to run on almost any database
      * SQL
      * NoSQL
    * Database only need implement a simple key/value store API
  * Support arbitrary user-defined types
    * There should be no distinction between built-in types and user types
    * User types should be index-able just like built-in types
  * Provide first class, built-in abstractions for Sets, Lists, and Maps
    * You can build anything out of these three collection types
  * Provide first class object references with strong referential integrity
    * Referential integrity implies reference fields are always indexed
    * Since they are always indexed, expose that "invert reference" capability to the application
    * Delete cascade behavior should be configurable just like in SQL
  * Make it easy to monitor for changes through arbitrary reference paths
    * Notify my `@OnChange` method when `"parent.friend.age"` changes
    * Maintain any custom indexes (derived information) in one place
  * Provide incremental validation support
    * Automatically run JSR 303 validation, but only on what's changed
    * Allow me to run pending validations at any time
  * Support painless "on-line" schema changes without downtime
    * Track object versions automatically
    * Invoke my `@OnVersionChange` method with old & new field values
  * Support object lifecycle notifications
    * `@OnCreate`
    * `@OnDelete`
  * Support copying object state into and out of transactions
    * "Snapshot" transactions retain a portion of transaction state indefinitely
  * Support a Java-centric command line interface (CLI)
