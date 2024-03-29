
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv;

import com.google.common.base.Converter;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jsimpledb.TestSupport;
import org.jsimpledb.kv.bdb.BerkeleyKVDatabase;
import org.jsimpledb.kv.fdb.FoundationKVDatabase;
import org.jsimpledb.kv.leveldb.LevelDBKVDatabase;
import org.jsimpledb.kv.simple.SimpleKVDatabase;
import org.jsimpledb.kv.sql.IsolationLevel;
import org.jsimpledb.kv.sql.MySQLKVDatabase;
import org.jsimpledb.kv.util.NavigableMapKVStore;
import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.ConvertedNavigableMap;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

public class KVDatabaseTest extends TestSupport {

    // Work around weird Cobertura class loading bug
    static {
        final SimpleKVDatabase db = new SimpleKVDatabase();
        final KVTransaction tx = db.createTransaction();
        new KVDatabaseException(db);
        new TransactionTimeoutException(tx);
        new StaleTransactionException(tx);
        new RetryTransactionException(tx);
        tx.rollback();
    }

    private ExecutorService executor;

    private SimpleKVDatabase simpleKV;
    private MySQLKVDatabase mysqlKV;
    private FoundationKVDatabase fdbKV;
    private BerkeleyKVDatabase bdbKV;
    private LevelDBKVDatabase leveldbKV;

    private long timeoutTestStartTime;

    @BeforeClass
    @Parameters("testSimpleKV")
    public void setTestSimpleKV(@Optional String testSimpleKV) {
        if (testSimpleKV != null && Boolean.valueOf(testSimpleKV))
            this.simpleKV = new SimpleKVDatabase(new NavigableMapKVStore(), 250, 500);
    }

    @BeforeClass
    @Parameters("mysqlURL")
    public void setMySQLURL(@Optional String mysqlURL) {
        if (mysqlURL != null) {
            final MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setUrl(mysqlURL);
            this.mysqlKV = new MySQLKVDatabase();
            this.mysqlKV.setDataSource(dataSource);
            this.mysqlKV.setIsolationLevel(IsolationLevel.SERIALIZABLE);
        }
    }

    @BeforeClass
    @Parameters("fdbClusterFile")
    public void setFoundationDBClusterFile(@Optional String fdbClusterFile) {
        if (fdbClusterFile != null) {
            this.fdbKV = new FoundationKVDatabase();
            this.fdbKV.setClusterFilePath(fdbClusterFile);
            this.fdbKV.start();
        }
    }

    @BeforeClass
    @Parameters("berkeleyDirPrefix")
    public void setBerkeleyDirPrefix(@Optional String berkeleyDirPrefix) throws IOException {
        if (berkeleyDirPrefix != null) {
            final File dir = File.createTempFile(berkeleyDirPrefix, null);
            Assert.assertTrue(dir.delete());
            Assert.assertTrue(dir.mkdirs());
            dir.deleteOnExit();
            this.bdbKV = new BerkeleyKVDatabase();
            this.bdbKV.setDirectory(dir);
            this.bdbKV.start();
        }
    }

    @BeforeClass
    @Parameters("levelDbDirPrefix")
    public void setLevelDbDirPrefix(@Optional String levelDbDirPrefix) throws IOException {
        if (levelDbDirPrefix != null) {
            final File dir = File.createTempFile(levelDbDirPrefix, null);
            Assert.assertTrue(dir.delete());
            Assert.assertTrue(dir.mkdirs());
            dir.deleteOnExit();
            this.leveldbKV = new LevelDBKVDatabase();
            this.leveldbKV.setDirectory(dir);
            this.leveldbKV.start();
        }
    }

    @BeforeClass
    public void setup() {
        this.executor = Executors.newFixedThreadPool(33);
    }

    @AfterClass
    public void teardown() {
        this.executor.shutdown();
        if (this.fdbKV != null)
            this.fdbKV.stop();
        if (this.bdbKV != null)
            this.bdbKV.stop();
        if (this.leveldbKV != null)
            this.leveldbKV.stop();
    }

    @DataProvider(name = "kvdbs")
    private Object[][] getDBs() throws Exception {
        final ArrayList<Object[]> list = new ArrayList<>();
        list.add(new Object[] { this.simpleKV });
        list.add(new Object[] { this.mysqlKV });
        list.add(new Object[] { this.fdbKV });
        list.add(new Object[] { this.bdbKV });
        list.add(new Object[] { this.leveldbKV });
        for (Iterator<Object[]> i = list.iterator(); i.hasNext(); ) {
            if (i.next()[0] == null)
                i.remove();
        }
        return list.toArray(new Object[list.size()][]);
    }

    @Test(dataProvider = "kvdbs")
    public void testSimpleStuff(KVDatabase store) throws Exception {

        // Clear database
        this.log.info("starting testSimpleStuff() on " + store);
        KVTransaction tx = store.createTransaction();
        tx.removeRange(null, null);
        tx.commit();

        // Verify database is empty
        tx = store.createTransaction();
        KVPair p = tx.getAtLeast(null);
        Assert.assertNull(p);
        p = tx.getAtMost(null);
        Assert.assertNull(p);
        Iterator<KVPair> it = tx.getRange(null, null, false);
        Assert.assertFalse(it.hasNext());
        tx.commit();

        // tx 1
        tx = store.createTransaction();
        byte[] x = tx.get(b("01"));
        Assert.assertNull(x);
        tx.put(b("01"), b("02"));
        Assert.assertEquals(tx.get(b("01")), b("02"));
        tx.commit();

        // tx 2
        tx = store.createTransaction();
        x = tx.get(b("01"));
        Assert.assertEquals(x, b("02"));
        tx.put(b("01"), b("03"));
        Assert.assertEquals(tx.get(b("01")), b("03"));
        tx.commit();

        // tx 3
        tx = store.createTransaction();
        x = tx.get(b("01"));
        Assert.assertEquals(x, b("03"));
        tx.put(b("10"), b("01"));
        tx.commit();

        // Check stale access
        try {
            x = tx.get(b("01"));
            assert false;
        } catch (StaleTransactionException e) {
            // expected
        }
        this.log.info("finished testSimpleStuff() on " + store);
    }

    @Test(dataProvider = "kvdbs")
    public void testConflictingTransactions(KVDatabase store) throws Exception {

        // Clear database
        this.log.info("starting testConflictingTransactions() on " + store);
        KVTransaction tx = store.createTransaction();
        tx.removeRange(null, null);
        tx.commit();

        // Both read the same key
        final KVTransaction[] txs = new KVTransaction[] { store.createTransaction(), store.createTransaction() };
        this.executor.submit(new Reader(txs[0], b("10"))).get();
        this.executor.submit(new Reader(txs[1], b("10"))).get();

        // Both write to the same key but with different values
        Boolean[] statuses = new Boolean[2];
        Future<?>[] futures = new Future<?>[] {
          this.executor.submit(new Writer(txs[0], b("10"), b("01"))),
          this.executor.submit(new Writer(txs[1], b("10"), b("02")))
        };

        // See what happened - we have have gotten a conflict at write time
        for (int i = 0; i < 2; i++) {
            try {
                futures[i].get();
                this.log.info(txs[i] + " #" + (i + 1) + " succeeded on write");
                statuses[i] = true;
            } catch (Exception e) {
                while (e instanceof ExecutionException)
                    e = (Exception)e.getCause();
                assert e instanceof RetryTransactionException : "wrong exception type: " + e;
                final RetryTransactionException retry = (RetryTransactionException)e;
                Assert.assertSame(retry.getTransaction(), txs[i]);
                this.log.info(txs[i] + " #" + (i + 1) + " failed on write");
                statuses[i] = false;
            }
        }

        // If both succeeded, then we should get a conflict on commit instead
        for (int i = 0; i < 2; i++) {
            if (statuses[i]) {
                this.showKV(txs[i], "tx[" + i + "] of " + store + " after write");
                futures[i] = this.executor.submit(new Committer(txs[i]));
            }
        }
        for (int i = 0; i < 2; i++) {
            if (statuses[i]) {
                try {
                    futures[i].get();
                    this.log.info(txs[i] + " #" + (i + 1) + " succeeded on commit");
                    statuses[i] = true;
                } catch (Exception e) {
                    while (e instanceof ExecutionException)
                        e = (Exception)e.getCause();
                    assert e instanceof RetryTransactionException : "wrong exception type: " + e;
                    final RetryTransactionException retry = (RetryTransactionException)e;
                    Assert.assertSame(retry.getTransaction(), txs[i]);
                    this.log.info(txs[i] + " #" + (i + 1) + " failed on commit");
                    statuses[i] = false;
                }
            }
        }

        // Exactly one should have failed and one should have succeeded
        assert statuses[0] ^ statuses[1] : "both transactions " + (statuses[0] ? "succeeded" : "failed");
        final byte[] expected = statuses[0] ? b("01") : b("02");
        final KVTransaction tx2 = store.createTransaction();
        this.showKV(tx2, "TX2 of " + store);
        byte[] x = this.executor.submit(new Reader(tx2, b("10"))).get();
        Assert.assertEquals(x, expected);
        tx2.rollback();
        this.log.info("finished testConflictingTransactions() on " + store);
    }

    @Test(dataProvider = "kvdbs")
    public void testNonconflictingTransactions(KVDatabase store) throws Exception {

        // Clear database
        this.log.info("starting testNonconflictingTransactions() on " + store);
        KVTransaction tx = store.createTransaction();
        tx.removeRange(null, null);
        tx.commit();

        // Multiple concurrent read-only transactions with overlapping read ranges and non-intersecting write ranges
        int done = 0;
        KVTransaction[] txs = new KVTransaction[10];
        for (int i = 0; i < txs.length; i++)
            txs[i] = store.createTransaction();
        while (true) {
            boolean finished = true;
            for (int i = 0; i < txs.length; i++) {
                if (txs[i] == null)
                    continue;
                finished = false;
                Future<?> rf = this.executor.submit(new Reader(txs[i], new byte[] { (byte)i }, true));
                Future<?> wf = this.executor.submit(new Writer(txs[i], new byte[] { (byte)(i + 128) }, b("02")));
                for (Future<?> f : new Future<?>[] { rf, wf }) {
                    try {
                        f.get();
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof RetryTransactionException) {
                            txs[i] = store.createTransaction();
                            break;
                        }
                        throw e;
                    }
                }
            }
            if (finished)
                break;
            for (int i = 0; i < txs.length; i++) {
                if (txs[i] == null)
                    continue;
                try {
                    txs[i].commit();
                } catch (RetryTransactionException e) {
                    txs[i] = store.createTransaction();
                    continue;
                }
                txs[i] = null;
            }
        }
        this.log.info("finished testNonconflictingTransactions() on " + store);
    }

    /**
     * This test runs transactions in parallel and verifies there is no "leakage" between them.
     * Database must be configured for serializable isolation.
     */
    @Test(dataProvider = "kvdbs")
    public void testParallelTransactions(KVDatabase store) throws Exception {
        this.log.info("starting testParallelTransactions() on " + store);
        for (int count = 0; count < 25; count++) {
            final RandomTask[] tasks = new RandomTask[25];
            for (int i = 0; i < tasks.length; i++) {
                tasks[i] = new RandomTask(i, store, this.random.nextLong());
                tasks[i].start();
            }
            for (int i = 0; i < tasks.length; i++)
                tasks[i].join();
            for (int i = 0; i < tasks.length; i++) {
                final Throwable fail = tasks[i].getFail();
                if (fail != null)
                    throw new Exception("task #" + i + " failed: >>>" + this.show(fail).trim() + "<<<");
            }
        }
        this.log.info("finished testParallelTransactions() on " + store);
        if (store instanceof Closeable)
            ((Closeable)store).close();
    }

    /**
     * This test runs transactions sequentially and verifies that each transaction sees
     * the changes that were committed in the previous transaction.
     */
    @Test(dataProvider = "kvdbs")
    public void testSequentialTransactions(KVDatabase store) throws Exception {
        this.log.info("starting testSequentialTransactions() on " + store);

        // Zero out database
        final KVTransaction tx = store.createTransaction();
        tx.removeRange(null, null);
        tx.commit();

        // Keep an in-memory record of what is in the committed database
        final TreeMap<byte[], byte[]> committedData = new TreeMap<byte[], byte[]>(ByteUtil.COMPARATOR);

        // Run transactions
        for (int i = 0; i < 50; i++) {
            final RandomTask task = new RandomTask(i, store, committedData, this.random.nextLong());
            task.run();
            final Throwable fail = task.getFail();
            if (fail != null)
                throw new Exception("task #" + i + " failed: >>>" + this.show(fail).trim() + "<<<");
        }
        this.log.info("finished testSequentialTransactions() on " + store);
    }

    @Test
    public void testSimpleKVTimeouts() throws Exception {

        // Test hold and wait timeouts both not attained
        this.timeoutTestStartTime = System.currentTimeMillis();
        SimpleKVDatabase store = new SimpleKVDatabase(200, 400);
        HolderThread holderThread = new HolderThread(store, 100);
        WaiterThread waiterThread = new WaiterThread(store, holderThread);
        holderThread.start();
        waiterThread.start();
        holderThread.join();
        waiterThread.join();
        Assert.assertEquals(holderThread.getResult(), "success");
        Assert.assertEquals(waiterThread.getResult(), "success");

        // Test wait but not hold timeout attained
        this.timeoutTestStartTime = System.currentTimeMillis();
        store = new SimpleKVDatabase(200, 400);
        holderThread = new HolderThread(store, 300);
        waiterThread = new WaiterThread(store, holderThread);
        holderThread.start();
        waiterThread.start();
        holderThread.join();
        waiterThread.join();
        Assert.assertEquals(holderThread.getResult(), "success");
        Assert.assertEquals(waiterThread.getResult(), "RetryTransactionException");

        // Test hold timeout by itself - no exception because nobody is waiting
        this.timeoutTestStartTime = System.currentTimeMillis();
        store = new SimpleKVDatabase(100, 100);
        holderThread = new HolderThread(store, 200);
        holderThread.start();
        holderThread.join();
        Assert.assertEquals(holderThread.getResult(), "success");

        // Test hold but not wait timeout attained - exception because somebody is waiting
        this.timeoutTestStartTime = System.currentTimeMillis();
        store = new SimpleKVDatabase(400, 200);
        holderThread = new HolderThread(store, 300);
        waiterThread = new WaiterThread(store, holderThread);
        holderThread.start();
        waiterThread.start();
        holderThread.join();
        waiterThread.join();
        Assert.assertEquals(holderThread.getResult(), "TransactionTimeoutException");
        Assert.assertEquals(waiterThread.getResult(), "success");
    }

// TestThread

    public abstract class TestThread extends Thread {

        protected final SimpleKVDatabase store;

        protected String result;

        protected TestThread(SimpleKVDatabase store) {
            this.store = store;
        }

        public String getResult() {
            return this.result;
        }

        @Override
        public final void run() {
            try {
                this.doTest();
                this.result = "success";
            } catch (Throwable t) {
                this.result = t.getClass().getSimpleName();
            } finally {
                this.log("result = " + this.result);
            }
        }

        protected abstract void doTest() throws Exception;

        protected void log(String message) {
//            final long offset = System.currentTimeMillis() - SimpleKVDatabaseTest.this.timeoutTestStartTime;
//            SimpleKVDatabaseTest.this.log.info(String.format("[%04d]: ", offset)
//              + this.getClass().getSimpleName() + ": " + message);
        }
    }

    public class HolderThread extends TestThread {

        protected final long delay;

        private boolean ready;

        public HolderThread(SimpleKVDatabase store, long delay) {
            super(store);
            this.delay = delay;
        }

        @Override
        public void doTest() throws Exception {
            this.log("creating transaction");
            final KVTransaction tx = this.store.createTransaction();
            this.log("-> put()");
            tx.put(new byte[0], new byte[0]);
            this.log("<- put()");
            synchronized (this) {
                this.ready = true;
                this.notifyAll();
            }
            this.log("-> sleep(" + this.delay + ")");
            Thread.sleep(this.delay);
            this.log("<- sleep(" + this.delay + ")");
            tx.commit();
        }

        public synchronized void waitUntilReady() throws InterruptedException {
            while (!this.ready)
                this.wait();
        }
    }

    public class WaiterThread extends TestThread {

        private final HolderThread holderThread;

        public WaiterThread(SimpleKVDatabase store, HolderThread holderThread) {
            super(store);
            this.holderThread = holderThread;
        }

        @Override
        public void doTest() throws Exception {
            this.log("waiting for holder");
            this.holderThread.waitUntilReady();
            this.log("creating transaction");
            final KVTransaction tx = this.store.createTransaction();
            this.log("-> get()");
            tx.get(new byte[0]);
            this.log("<- get()");
            tx.commit();
        }
    }

// RandomTask

    public class RandomTask extends Thread {

        private final int id;
        private final KVDatabase store;
        private final Random random;
        private final NavigableMap<byte[], byte[]> committedData;       // current committed data, if known
        private final Converter<String, byte[]> converter = ByteUtil.STRING_CONVERTER.reverse();

        private Throwable fail;

        public RandomTask(int id, KVDatabase store, long seed) {
            this(id, store, null, seed);
        }

        public RandomTask(int id, KVDatabase store, NavigableMap<byte[], byte[]> committedData, long seed) {
            super("Random[" + id + "]");
            this.log("seed = " + seed);
            this.id = id;
            this.store = store;
            this.committedData = committedData;
            this.random = new Random(seed);
        }

        @Override
        public void run() {
            try {
                this.test();
                this.log("succeeded");
            } catch (Throwable t) {
                this.log("failed: " + t);
                this.fail = t;
            }
        }

        public Throwable getFail() {
            return this.fail;
        }

        private void test() throws Exception {

            // Create transaction
            final TreeMap<byte[], byte[]> knownValues = new TreeMap<byte[], byte[]>(ByteUtil.COMPARATOR);
            final Map<String, String> knownValuesView = new ConvertedNavigableMap<String, String, byte[], byte[]>(
              knownValues, this.converter, this.converter);
            final KVTransaction tx = this.store.createTransaction();

            // Load committed database contents into "known values" tracker
            if (this.committedData != null)
                knownValues.putAll(this.committedData);
            final Map<String, String> committedDataView = this.committedData != null ?
              new ConvertedNavigableMap<String, String, byte[], byte[]>(this.committedData, this.converter, this.converter) : null;

            // Verify committed data before starting
            if (this.committedData != null) {
                final TreeMap<byte[], byte[]> actualValues = this.readDatabase(tx);
                Assert.assertEquals(
                  new ConvertedNavigableMap<String, String, byte[], byte[]>(actualValues, this.converter, this.converter),
                  knownValuesView);
            }

            // Make a bunch of random changes
            boolean knownValuesChanged = false;
            try {
                final int limit = this.r(1000);
                for (int j = 0; j < limit; j++) {
                    byte[] key;
                    byte[] val;
                    byte[] min;
                    byte[] max;
                    KVPair pair;
                    int option = this.r(55);
                    if (option < 10) {                                              // get
                        key = this.rb(2, false);
                        val = tx.get(key);
                        this.log("get: " + s(key) + " -> " + s(val));
                        if (val == null)
                            Assert.assertTrue(!knownValues.containsKey(key));
                        else if (knownValues.containsKey(key))
                            Assert.assertEquals(knownValues.get(key), val);
                        else {
                            knownValues.put(key, val);
                            knownValuesChanged = true;
                        }
                    } else if (option < 20) {                                       // put
                        key = this.rb(2, false);
                        val = this.rb(2, true);
                        this.log("put: " + s(key) + " -> " + s(val));
                        tx.put(key, val);
                        knownValues.put(key, val);
                        knownValuesChanged = true;
                    } else if (option < 30) {                                       // getAtLeast
                        min = this.rb(2, true);
                        pair = tx.getAtLeast(min);
                        this.log("getAtLeast: " + s(min) + " -> " + s(pair));
                        if (pair == null)
                            Assert.assertTrue(knownValues.tailMap(min).isEmpty());
                        else if (knownValues.containsKey(pair.getKey()))
                            Assert.assertEquals(knownValues.get(pair.getKey()), pair.getValue());
                        else {
                            knownValues.put(pair.getKey(), pair.getValue());
                            knownValuesChanged = true;
                        }
                    } else if (option < 40) {                                       // getAtMost
                        max = this.rb(2, true);
                        pair = tx.getAtMost(max);
                        this.log("getAtMost: " + s(max) + " -> " + s(pair));
                        if (pair == null)
                            Assert.assertTrue(knownValues.headMap(max).isEmpty());
                        else if (knownValues.containsKey(pair.getKey()))
                            Assert.assertEquals(knownValues.get(pair.getKey()), pair.getValue());
                        else {
                            knownValues.put(pair.getKey(), pair.getValue());
                            knownValuesChanged = true;
                        }
                    } else if (option < 50) {                                       // remove
                        key = this.rb(2, false);
                        this.log("remove: " + s(key));
                        tx.remove(key);
                        knownValues.remove(key);
                        knownValuesChanged = true;
                    } else if (option < 52) {                                       // removeRange
                        min = this.rb2(2, 20);
                        do {
                            max = this.rb2(2, 30);
                        } while (max != null && min != null && ByteUtil.COMPARATOR.compare(min, max) > 0);
                        this.log("removeRange: " + s(min) + " to " + s(max));
                        tx.removeRange(min, max);
                        if (min == null && max == null)
                            knownValues.clear();
                        else if (min == null)
                            knownValues.headMap(max).clear();
                        else if (max == null)
                            knownValues.tailMap(min).clear();
                        else
                            knownValues.subMap(min, max).clear();
                        knownValuesChanged = true;
                    } else {                                                        // sleep
                        this.log("sleep");
                        try {
                            Thread.sleep(this.r(50));
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                    if (knownValuesChanged)
                        this.log("new knownValues: " + knownValuesView);
                }

                // TODO: keep track of removes as well as knownValues
                for (Map.Entry<byte[], byte[]> entry : knownValues.entrySet()) {
                    final byte[] key = entry.getKey();
                    final byte[] val = entry.getValue();
                    final byte[] txVal = tx.get(key);
                    Assert.assertEquals(txVal, val, "tx has " + s(txVal) + " for key " + s(key)
                      + " but knownValues has:\n*** KNOWN VALUES: " + knownValuesView);
                }

                // Maybe commit
                final boolean rollback = this.r(5) == 3;
                if (rollback) {
                    tx.rollback();
                    this.log("rolled-back");
                } else {
                    tx.commit();
                    if (this.committedData != null) {
                        this.committedData.clear();
                        this.committedData.putAll(knownValues);
                    }
                    this.log("committed");
                }

            } catch (TransactionTimeoutException e) {
                this.log("got " + e);
            } catch (RetryTransactionException e) {
                this.log("got " + e);
            }

            // Verify committed database contents are equal to what's expected
            if (this.committedData != null) {
                knownValues.clear();
                knownValues.putAll(this.readDatabase());
                Assert.assertEquals(knownValuesView, committedDataView,
                  "\n*** ACTUAL:\n" + knownValuesView + "\n*** EXPECTED:\n" + committedDataView + "\n");
            }
        }

        private TreeMap<byte[], byte[]> readDatabase() {
            final KVTransaction tx = this.store.createTransaction();
            final TreeMap<byte[], byte[]> values = this.readDatabase(tx);
            tx.commit();
            return values;
        }

        private TreeMap<byte[], byte[]> readDatabase(KVTransaction tx) {
            final TreeMap<byte[], byte[]> values = new TreeMap<byte[], byte[]>(ByteUtil.COMPARATOR);
            final Iterator<KVPair> i = tx.getRange(null, null, false);
            while (i.hasNext()) {
                final KVPair pair = i.next();
                values.put(pair.getKey(), pair.getValue());
            }
            if (i instanceof AutoCloseable) {
                try {
                    ((AutoCloseable)i).close();
                } catch (Exception e) {
                    // ignore
                }
            }
            return values;
        }

        private void log(String s) {
            //System.out.println("Random[" + this.id + "]: " + s);
        }

        private int r(int max) {
            return this.random.nextInt(max);
        }

        private byte[] rb(int len, boolean allowFF) {
            final byte[] b = new byte[this.r(len) + 1];
            this.random.nextBytes(b);
            if (!allowFF && b[0] == (byte)0xff)
                b[0] = (byte)random.nextInt(0xff);
            return b;
        }

        private byte[] rb2(int len, int nullchance) {
            if (this.r(nullchance) == 0)
                return null;
            return this.rb(len, true);
        }
    }

// Reader

    public class Reader implements Callable<byte[]> {

        final KVTransaction tx;
        final byte[] key;
        final boolean range;

        public Reader(KVTransaction tx, byte[] key, boolean range) {
            this.tx = tx;
            this.key = key;
            this.range = range;
        }

        public Reader(KVTransaction tx, byte[] key) {
            this(tx, key, false);
        }

        @Override
        public byte[] call() {
            if (this.range) {
                final KVPair pair = this.tx.getAtLeast(this.key);
                KVDatabaseTest.this.log.info("reading at least " + s(this.key) + " -> " + pair + " in " + this.tx);
                return pair != null ? pair.getValue() : null;
            } else {
                final byte[] value = this.tx.get(this.key);
                KVDatabaseTest.this.log.info("reading " + s(this.key) + " -> " + s(value) + " in " + this.tx);
                return value;
            }
        }
    }

// Writer

    public class Writer implements Runnable {

        final KVTransaction tx;
        final byte[] key;
        final byte[] value;

        public Writer(KVTransaction tx, byte[] key, byte[] value) {
            this.tx = tx;
            this.key = key;
            this.value = value;
        }

        @Override
        public void run() {
            try {
                KVDatabaseTest.this.log.info("putting " + s(this.key) + " -> " + s(this.value) + " in " + this.tx);
                this.tx.put(this.key, this.value);
            } catch (RuntimeException e) {
                KVDatabaseTest.this.log.info("exception putting " + s(this.key) + " -> " + s(this.value)
                  + " in " + this.tx + ": " + e);
                throw e;
            }
        }
    }

// Committer

    public class Committer implements Runnable {

        final KVTransaction tx;

        public Committer(KVTransaction tx) {
            this.tx = tx;
        }

        @Override
        public void run() {
            try {
                KVDatabaseTest.this.log.info("committing " + this.tx);
                this.tx.commit();
            } catch (RuntimeException e) {
                KVDatabaseTest.this.log.info("exception committing " + this.tx + ": " + e);
                throw e;
            }
        }
    }
}

