/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package gov.nasa.worldwind.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BetterBasicMemoryCacheTest {

    @Test
    void add() {
        BetterBasicMemoryCache cache = new BetterBasicMemoryCache(10);

        assertEquals(10, cache.getCapacity());
        assertEquals(10, cache.getFreeCapacity());
        assertEquals(0, cache.getNumObjects());

        {
            Object a = new Object();
            cache.add(a, a, 1);

            assertEquals(9, cache.getFreeCapacity());
            assertEquals(1, cache.getNumObjects());
        }


        {
            Object a = new Object();
            cache.add(a, a, 2);

            assertEquals(7, cache.getFreeCapacity());
            assertEquals(2, cache.getNumObjects());
        }

        {
            Object a = new Object();
            cache.add(a, a, 8);

            assertTrue(cache.getFreeCapacity() < 2);
            assertTrue(cache.getNumObjects() <= 2);
        }
    }

    @Test
    void cacheListener() {
        BetterBasicMemoryCache cache = new BetterBasicMemoryCache(1);

        final AtomicReference<Object> removed = new AtomicReference<>();
        cache.addCacheListener(new MemoryCache.CacheListener() {
            @Override
            public void entryRemoved(Object key, Object clientObject) {
                removed.set(clientObject);
            }

            @Override
            public void removalException(Throwable exception, Object key, Object clientObject) {

            }
        });

        Object a = new Object();
        cache.add(a, a, 1);
        assertEquals(cache.getNumObjects(), 1);

        Object b = new Object();
        cache.add(b, b, 1);
        assertEquals(cache.getNumObjects(), 1);

        assertEquals(removed.get(), a);
    }
}