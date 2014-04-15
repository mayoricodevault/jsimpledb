
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.jsimpledb.kv.simple;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dellroad.stuff.string.ByteArrayEncoder;
import org.jsimpledb.TestSupport;
import org.jsimpledb.kv.KVDatabase;
import org.jsimpledb.kv.KVPair;
import org.jsimpledb.kv.KVTransaction;
import org.jsimpledb.kv.RetryTransactionException;
import org.jsimpledb.kv.StaleTransactionException;
import org.jsimpledb.kv.util.NavigableMapKVStore;
import org.jsimpledb.util.ByteUtil;
import org.jsimpledb.util.ConvertedNavigableMap;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SimpleKVDatabaseTest extends TestSupport {

    private ExecutorService executor;

    @BeforeClass
    public void setup() {
        this.executor = Executors.newFixedThreadPool(33);
    }

    @AfterClass
    public void teardown() {
        this.executor.shutdown();
    }

    @Test
    public void test1() throws Exception {
        final SimpleKVDatabase store = new SimpleKVDatabase(5, 10);

        // tx 1
        KVTransaction tx = store.createTransaction();
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
        tx.put(b("10"), b("20"));
        tx.commit();
        try {
            x = tx.get(b("01"));
            assert false;
        } catch (StaleTransactionException e) {
            // expected
        }

        // One transaction deadlocking on another
        final KVTransaction tx2 = store.createTransaction();
        final KVTransaction tx3 = store.createTransaction();
        this.executor.submit(new Writer(tx2, b("01"), b("04"))).get();
        final Reader tx3reader = new Reader(tx3, b("01"));
        try {
            this.executor.submit(tx3reader).get();
            assert false;
        } catch (ExecutionException e) {
            Assert.assertEquals(e.getCause().getClass(), RetryTransactionException.class);
        }
        tx2.commit();
        final KVTransaction tx4 = store.createTransaction();
        x = this.executor.submit(new Reader(tx4, b("01"))).get();
        Assert.assertEquals(x, b("04"));
        tx4.rollback();

        // Multiple concurrent read-only transactions with overlapping read ranges and non-intersecting write ranges
        KVTransaction[] txs = new KVTransaction[10];
        for (int i = 0; i < txs.length; i++)
            txs[i] = store.createTransaction();
        for (int i = 0; i < txs.length; i++) {
            this.executor.submit(new Reader(txs[i], new byte[] { (byte)i }, true)).get();
            this.executor.submit(new Writer(txs[i], new byte[] { (byte)(i + 128) }, b("02"))).get();
        }
        for (int i = 0; i < txs.length; i++)
            txs[i].commit();
    }

    @Test
    public void test2() throws Exception {
        final SimpleKVDatabase store = new SimpleKVDatabase(5, 10);
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
    }

    @Test
    public void test3() throws Exception {
        final TreeMap<byte[], byte[]> storeData = new TreeMap<byte[], byte[]>(ByteUtil.COMPARATOR);
        final NavigableMapKVStore kv = new NavigableMapKVStore(storeData);
        final SimpleKVDatabase store = new SimpleKVDatabase(kv, 5, 10);
        for (int i = 0; i < 50; i++) {
            final RandomTask task = new RandomTask(i, store, storeData, this.random.nextLong());
            task.run();
            final Throwable fail = task.getFail();
            if (fail != null)
                throw new Exception("task #" + i + " failed: >>>" + this.show(fail).trim() + "<<<");
        }
    }

    public static byte[] b(String s) {
        return ByteArrayEncoder.decode(s);
    }

// RandomTask

    public class RandomTask extends Thread {

        private final int id;
        private final KVDatabase store;
        private final Random random;
        private final TreeMap<byte[], byte[]> storeData;
        private final StringToByteArrayConverter converter = new StringToByteArrayConverter();

        private Throwable fail;

        public RandomTask(int id, KVDatabase store, long seed) {
            this(id, store, null, seed);
        }

        public RandomTask(int id, KVDatabase store, TreeMap<byte[], byte[]> storeData, long seed) {
            super("Random[" + id + "]");
            this.log("seed = " + seed);
            this.id = id;
            this.store = store;
            this.storeData = storeData;
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

            // Snapshot database, if known
            if (this.storeData != null)
                knownValues.putAll(this.storeData);
            final Map<String, String> storeDataView = this.storeData != null ?
              new ConvertedNavigableMap<String, String, byte[], byte[]>(this.storeData, this.converter, this.converter) : null;

            // Make a bunch of random changes
            boolean knownValuesChanged = false;
            try {
                for (int j = 0; j < this.r(1000); j++) {
                    byte[] key;
                    byte[] val;
                    byte[] min;
                    byte[] max;
                    KVPair pair;
                    int option = this.r(55);
                    if (option < 10) {                                              // get
                        key = this.rb(2, false);
                        val = tx.get(key);
                        this.log("get: " + p(key) + " -> " + p(val));
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
                        this.log("put: " + p(key) + " -> " + p(val));
                        tx.put(key, val);
                        knownValues.put(key, val);
                        knownValuesChanged = true;
                    } else if (option < 30) {                                       // getAtLeast
                        min = this.rb(2, true);
                        pair = tx.getAtLeast(min);
                        this.log("getAtLeast: " + p(min) + " -> " + p(pair));
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
                        this.log("getAtMost: " + p(max) + " -> " + p(pair));
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
                        this.log("remove: " + p(key));
                        tx.remove(key);
                        knownValues.remove(key);
                        knownValuesChanged = true;
                    } else if (option < 52) {                                       // removeRange
                        min = this.rb2(2, 20);
                        do {
                            max = this.rb2(2, 30);
                        } while (max != null && min != null && ByteUtil.COMPARATOR.compare(min, max) > 0);
                        this.log("removeRange: " + p(min) + " to " + p(max));
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
                    Assert.assertEquals(txVal, val, "tx has " + p(txVal) + " for key " + p(key)
                      + " but knownValues has:\n*** KNOWN VALUES: " + knownValuesView);
                }

                // Maybe commit
                final boolean rollback = this.r(5) != 3;
                if (rollback) {
                    tx.rollback();
                    this.log("rolled-back");
                } else {
                    tx.commit();
                    this.log("committed");
                }

                // Verify contents equal what's expected
                if (!rollback && this.storeData != null) {
                    Assert.assertEquals(storeDataView, knownValuesView,
                      "\n*** ACTUAL:\n" + storeDataView + "\n*** EXPECTED:\n" + knownValuesView + "\n");
                }

            } catch (RetryTransactionException e) {
                this.log("got " + e);
            }
        }

        private void log(String s) {
            //System.out.println("Random[" + this.id + "]: " + s);
        }

        private String p(byte[] val) {
            return ByteUtil.toString(val);
        }

        private String p(KVPair pair) {
            return pair != null ? ("[" + p(pair.getKey()) + ", " + p(pair.getValue()) + "]") : "null";
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

    public static class Reader implements Callable<byte[]> {

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
                return pair != null ? pair.getValue() : null;
            } else
                return this.tx.get(this.key);
        }
    }

// Writer

    public static class Writer implements Runnable {

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
            this.tx.put(this.key, this.value);
        }
    }
}
