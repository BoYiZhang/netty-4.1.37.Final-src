/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;


import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.buffer.PoolArena.SizeClass;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acts a Thread cache for allocations. This implementation is moduled after
 * <a href="http://people.freebsd.org/~jasone/jemalloc/bsdcan2006/jemalloc.pdf">jemalloc</a> and the descripted
 * technics of
 * <a href="https://www.facebook.com/notes/facebook-engineering/scalable-memory-allocation-using-jemalloc/480222803919">
 * Scalable memory allocation using jemalloc</a>.
 *
 * 每一个线程
 */
final class PoolThreadCache {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PoolThreadCache.class);



    //todo 对应的 Heap PoolArena 对象
    final PoolArena<byte[]> heapArena;

    //todo 对应的 Direct PoolArena 对象
    final PoolArena<ByteBuffer> directArena;



    // Hold the caches for the different size classes, which are tiny, small and normal.

    //todo Heap 三种类型的缓存

    //todo Heap 类型的 tiny Subpage 内存块缓存数组
    private final MemoryRegionCache<byte[]>[] tinySubPageHeapCaches;
    //todo Heap 类型的 small Subpage 内存块缓存数组
    private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;

    //todo  Heap 类型的 normal 内存块缓存数组
    private final MemoryRegionCache<byte[]>[] normalHeapCaches;


    //todo Direct 三种类型的缓存

    //todo  Direct 类型的 tiny Subpage 内存块缓存数组
    private final MemoryRegionCache<ByteBuffer>[] tinySubPageDirectCaches;

    //todo Direct 类型的 small Subpage 内存块缓存数组
    private final MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;

    //todo  Direct 类型的 normal 内存块缓存数组
    private final MemoryRegionCache<ByteBuffer>[] normalDirectCaches;


    // Used for bitshifting when calculate the index of normal caches later

    //todo  用于计算请求分配的 normal 类型的内存块，在 {@link #normalDirectCaches} 数组中的位置
    //todo  默认为 log2(pageSize) = log2(8192) = 13
    private final int numShiftsNormalDirect;
    private final int numShiftsNormalHeap;

    /**
     * {@link #allocations} 到达该阀值，释放缓存
     *
     * 默认为 8192 次
     *
     * @see #
     */
    private final int freeSweepAllocationThreshold;


    private final AtomicBoolean freed = new AtomicBoolean();

    //todo 分配次数
    private int allocations;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolThreadCache(PoolArena<byte[]> heapArena, PoolArena<ByteBuffer> directArena,
                    int tinyCacheSize, int smallCacheSize, int normalCacheSize,
                    int maxCachedBufferCapacity, int freeSweepAllocationThreshold) {
        checkPositiveOrZero(maxCachedBufferCapacity, "maxCachedBufferCapacity");
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        this.heapArena = heapArena;
        this.directArena = directArena;

        //todo  初始化 Direct 类型的内存块缓存
        if (directArena != null) {


            //todo  tinyCacheSize =numTinySubpagePools 512
            //todo  PoolArena. = 32

            //todo 创建 tinySubPageDirectCaches  数组默认值: 32 , 队列长度为 512
            tinySubPageDirectCaches = createSubPageCaches(
                    tinyCacheSize, PoolArena.numTinySubpagePools, SizeClass.Tiny);


            //todo 创建 smallSubPageDirectCaches  数组默认值:4
            smallSubPageDirectCaches = createSubPageCaches(
                    smallCacheSize, directArena.numSmallSubpagePools, SizeClass.Small);

            //todo 计算 numShiftsNormalDirect

            //todo 默认 13   :  log2(8192) = 13
            numShiftsNormalDirect = log2(directArena.pageSize);

            //todo 创建 normalDirectCaches
            normalDirectCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, directArena);

            //todo 增加 directArena 的线程引用计数
            directArena.numThreadCaches.getAndIncrement();
        } else {
            // No directArea is configured so just null out all caches
            tinySubPageDirectCaches = null;
            smallSubPageDirectCaches = null;
            normalDirectCaches = null;
            numShiftsNormalDirect = -1;
        }

        //todo 初始化 Heap 类型的内存块缓存。同上面部分。
        if (heapArena != null) {

            // Create the caches for the heap allocations
            tinySubPageHeapCaches = createSubPageCaches(
                    tinyCacheSize, PoolArena.numTinySubpagePools, SizeClass.Tiny);
            smallSubPageHeapCaches = createSubPageCaches(
                    smallCacheSize, heapArena.numSmallSubpagePools, SizeClass.Small);

            numShiftsNormalHeap = log2(heapArena.pageSize);
            normalHeapCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, heapArena);

            heapArena.numThreadCaches.getAndIncrement();

        } else {
            // No heapArea is configured so just null out all caches
            tinySubPageHeapCaches = null;
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
            numShiftsNormalHeap = -1;
        }

        //todo 校验参数，保证 PoolThreadCache 可缓存内存块。
        // Only check if there are caches in use.
        if ((tinySubPageDirectCaches != null || smallSubPageDirectCaches != null || normalDirectCaches != null
                || tinySubPageHeapCaches != null || smallSubPageHeapCaches != null || normalHeapCaches != null)
                && freeSweepAllocationThreshold < 1) {
            throw new IllegalArgumentException("freeSweepAllocationThreshold: "
                    + freeSweepAllocationThreshold + " (expected: > 0)");
        }
    }

    //todo  tiny 类型，
    //     默认 cacheSize = PooledByteBufAllocator.DEFAULT_TINY_CACHE_SIZE = 512 ,
    //     numCaches = PoolArena.numTinySubpagePools = 512 >>> 4 = 32


    //todo  small 类型，默认 cacheSize = PooledByteBufAllocator.DEFAULT_SMALL_CACHE_SIZE = 256 ,
    //      numCaches = pageSize - 9 = 13 - 9 = 4
    private static <T> MemoryRegionCache<T>[] createSubPageCaches(
            int cacheSize, int numCaches, SizeClass sizeClass) {
        if (cacheSize > 0 && numCaches > 0) {
            @SuppressWarnings("unchecked")


            // todo 创建一个MemoryRegionCache 数组
            //        tiny   [ 32 ]    0     16B    32B    .....   N*16B  ( 下标 0 为 null)
            //        small  [ 4  ]    512B  1K     2K     4K
            //        normal [ 3 ]     8K    16K    32K
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[numCaches];


            for (int i = 0; i < cache.length; i++) {

                // TODO: maybe use cacheSize / cache.length
                // TODO:
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize, sizeClass);


            }
            return cache;
        } else {
            return null;
        }
    }

    //todo  normal 类型，默认 cacheSize = PooledByteBufAllocator.DEFAULT_NORMAL_CACHE_SIZE = 64 ,
    // maxCachedBufferCapacity = PoolArena.DEFAULT_MAX_CACHED_BUFFER_CAPACITY = 32 * 1024 = 32KB
    private static <T> MemoryRegionCache<T>[] createNormalCaches(
            int cacheSize, int maxCachedBufferCapacity, PoolArena<T> area) {
        if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);
            int arraySize = Math.max(1, log2(max / area.pageSize) + 1);

            @SuppressWarnings("unchecked")
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[arraySize];
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new NormalMemoryRegionCache<T>(cacheSize);
            }

            //todo   cache[0] = 8KB、cache[1] = 16KB、cache[2] = 32KB 。

            return cache;
        } else {
            return null;
        }
    }

    private static int log2(int val) {
        int res = 0;
        while (val > 1) {
            val >>= 1;
            res++;
        }
        return res;
    }

    /**
     * Try to allocate a tiny buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateTiny(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        //todo cacheForTiny
        return allocate(cacheForTiny(area, normCapacity), buf, reqCapacity);
    }

    /**
     * Try to allocate a small buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateSmall(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForSmall(area, normCapacity), buf, reqCapacity);
    }

    /**
     * Try to allocate a small buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateNormal(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForNormal(area, normCapacity), buf, reqCapacity);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean allocate(MemoryRegionCache<?> cache, PooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        //todo 从队列中拿出 Entry 空间
        // 分配内存块，并初始化到 MemoryRegionCache 中
        boolean allocated = cache.allocate(buf, reqCapacity);

        //todo 到达阀值，整理缓存
        if (++ allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        //todo 返回是否分配成功
        return allocated;
    }

    /**
     * Add {@link PoolChunk} and {@code handle} to the cache if there is enough room.
     * Returns {@code true} if it fit into the cache {@code false} otherwise.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    boolean add(PoolArena<?> area, PoolChunk chunk, ByteBuffer nioBuffer,
                long handle, int normCapacity, SizeClass sizeClass) {

        //todo 获得对应的 MemoryRegionCache 对象
        MemoryRegionCache<?> cache = cache(area, normCapacity, sizeClass);
        if (cache == null) {
            return false;
        }
        //todo 添加到 MemoryRegionCache 内存块中
        return cache.add(chunk, nioBuffer, handle);
    }

    private MemoryRegionCache<?> cache(PoolArena<?> area, int normCapacity, SizeClass sizeClass) {
        switch (sizeClass) {
        case Normal:
            return cacheForNormal(area, normCapacity);
        case Small:
            return cacheForSmall(area, normCapacity);
        case Tiny:
            return cacheForTiny(area, normCapacity);
        default:
            throw new Error();
        }
    }

    /// TODO: In the future when we move to Java9+ we should use java.lang.ref.Cleaner.
    @Override
    protected void finalize() throws Throwable {
        try {
            // 调用父方法
            super.finalize();
        } finally {
            free(true);
        }
    }

    /**
     *  Should be called if the Thread that uses this cache is about to exist to release resources out of the cache
     */
    void free(boolean finalizer) {
        // As free() may be called either by the finalizer or by FastThreadLocal.onRemoval(...) we need to ensure
        // we only call this one time.

        // todo 清空缓存
        if (freed.compareAndSet(false, true)) {
            int numFreed = free(tinySubPageDirectCaches, finalizer) +
                    free(smallSubPageDirectCaches, finalizer) +
                    free(normalDirectCaches, finalizer) +
                    free(tinySubPageHeapCaches, finalizer) +
                    free(smallSubPageHeapCaches, finalizer) +
                    free(normalHeapCaches, finalizer);

            if (numFreed > 0 && logger.isDebugEnabled()) {
                logger.debug("Freed {} thread-local buffer(s) from thread: {}", numFreed,
                        Thread.currentThread().getName());
            }

            //todo  减小 directArena 的线程引用计数
            if (directArena != null) {
                directArena.numThreadCaches.getAndDecrement();
            }

            // todo 减小 heapArena 的线程引用计数
            if (heapArena != null) {
                heapArena.numThreadCaches.getAndDecrement();
            }
        }
    }

    private static int free(MemoryRegionCache<?>[] caches, boolean finalizer) {
        if (caches == null) {
            return 0;
        }

        int numFreed = 0;
        for (MemoryRegionCache<?> c: caches) {
            numFreed += free(c, finalizer);
        }
        return numFreed;
    }

    private static int free(MemoryRegionCache<?> cache, boolean finalizer) {
        if (cache == null) {
            return 0;
        }
        return cache.free(finalizer);
    }

    void trim() {
        trim(tinySubPageDirectCaches);
        trim(smallSubPageDirectCaches);
        trim(normalDirectCaches);
        trim(tinySubPageHeapCaches);
        trim(smallSubPageHeapCaches);
        trim(normalHeapCaches);
    }

    private static void trim(MemoryRegionCache<?>[] caches) {
        if (caches == null) {
            return;
        }
        for (MemoryRegionCache<?> c: caches) {
            trim(c);
        }
    }

    private static void trim(MemoryRegionCache<?> cache) {
        if (cache == null) {
            return;
        }
        cache.trim();
    }

    private MemoryRegionCache<?> cacheForTiny(PoolArena<?> area, int normCapacity) {

        //todo  normCapacity 除以 16.  计算数组下标
        int idx = PoolArena.tinyIdx(normCapacity);
        if (area.isDirect()) {
            return cache(tinySubPageDirectCaches, idx);
        }

        return cache(tinySubPageHeapCaches, idx);
    }

    private MemoryRegionCache<?> cacheForSmall(PoolArena<?> area, int normCapacity) {
        //todo  获得数组下标
        int idx = PoolArena.smallIdx(normCapacity);
        if (area.isDirect()) {
            return cache(smallSubPageDirectCaches, idx);
        }
        return cache(smallSubPageHeapCaches, idx);
    }

    private MemoryRegionCache<?> cacheForNormal(PoolArena<?> area, int normCapacity) {
        if (area.isDirect()) {

            //todo  获得数组下标
            int idx = log2(normCapacity >> numShiftsNormalDirect);
            return cache(normalDirectCaches, idx);
        }
        //todo  获得数组下标
        int idx = log2(normCapacity >> numShiftsNormalHeap);
        return cache(normalHeapCaches, idx);
    }

    private static <T> MemoryRegionCache<T> cache(MemoryRegionCache<T>[] cache, int idx) {
        if (cache == null || idx > cache.length - 1) {
            return null;
        }
        return cache[idx];
    }

    /**
     * Cache used for buffers which are backed by TINY or SMALL size.
     */
    private static final class SubPageMemoryRegionCache<T> extends MemoryRegionCache<T> {
        SubPageMemoryRegionCache(int size, SizeClass sizeClass) {
            super(size, sizeClass);
        }

        //todo  初始化
        @Override
        protected void initBuf(
                PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, PooledByteBuf<T> buf, int reqCapacity) {
            chunk.initBufWithSubpage(buf, nioBuffer, handle, reqCapacity);
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    private static final class NormalMemoryRegionCache<T> extends MemoryRegionCache<T> {
        NormalMemoryRegionCache(int size) {
            super(size, SizeClass.Normal);
        }

        @Override
        protected void initBuf(
                PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, PooledByteBuf<T> buf, int reqCapacity) {
            chunk.initBuf(buf, nioBuffer, handle, reqCapacity);
        }
    }

    private abstract static class MemoryRegionCache<T> {

        //todo {@link #queue} 队列大小
        private final int size;

        //todo 队列。里面存储内存块
        private final Queue<Entry<T>> queue;

        //TODO Tiny, Small, Normal
        private final SizeClass sizeClass;

        //todo  分配次数计数器
        private int allocations;

        MemoryRegionCache(int size, SizeClass sizeClass) {
            this.size = MathUtil.safeFindNextPositivePowerOfTwo(size);
            queue = PlatformDependent.newFixedMpscQueue(this.size);
            this.sizeClass = sizeClass;
        }

        /**
         * Init the {@link PooledByteBuf} using the provided chunk and handle with the capacity restrictions.
         */
        protected abstract void initBuf(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle,
                                        PooledByteBuf<T> buf, int reqCapacity);

        /**
         * Add to cache if not already full.
         */
        @SuppressWarnings("unchecked")
        public final boolean add(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle) {

            //todo 创建 Entry 对象
            Entry<T> entry = newEntry(chunk, nioBuffer, handle);

            //todo 添加到队列
            boolean queued = queue.offer(entry);

            //todo  若添加失败，说明队列已满，回收 Entry 对象
            if (!queued) {
                // If it was not possible to cache the chunk, immediately recycle the entry
                entry.recycle();
            }

            //todo 是否添加成功
            return queued;
        }

        /**
         * todo 从 queue 中弹出一个 entry 给 ByteBuf 初始化
         * Allocate something out of the cache if possible and remove the entry from the cache.
         */
        public final boolean allocate(PooledByteBuf<T> buf, int reqCapacity) {
            //todo 获取并移除队列首个 Entry 对象
            Entry<T> entry = queue.poll();

            //todo 获取失败，返回 false
            if (entry == null) {
                return false;
            }
            //todo 初始化 初始化内存块到 PooledByteBuf 对象中
            initBuf(entry.chunk, entry.nioBuffer, entry.handle, buf, reqCapacity);
            //todo
            entry.recycle();


            //todo 增加 allocations 计数。因为分配总是在相同线程，所以不需要考虑线程安全的问题
            //    allocations is not thread-safe which is fine as this is only called from the same thread all time.
            ++ allocations;
            return true;
        }

        /**
         * Clear out this cache and free up all previous cached {@link PoolChunk}s and {@code handle}s.
         */
        public final int free(boolean finalizer) {
            return free(Integer.MAX_VALUE, finalizer);
        }


        //todo 清除队列中的指定数量元素
        private int free(int max, boolean finalizer) {
            int numFreed = 0;
            for (; numFreed < max; numFreed++) {

                //todo 获取并移除首元素
                Entry<T> entry = queue.poll();
                if (entry != null) {

                    //todo 释放缓存的内存块回 Chunk 中
                    freeEntry(entry, finalizer);
                } else {
                    // all cleared
                    return numFreed;
                }
            }
            return numFreed;
        }

        /**
         * todo 在分配过程还有一个trim()方法，当分配操作达到一定阈值（Netty默认8192）时，
         *      没有被分配出去的缓存空间都要被释放，以防止内存泄漏，核心代码如下：
         * Free up cached {@link PoolChunk}s if not allocated frequently enough.
         */
        public final void trim() {

            //todo allocations 表示已经重新分配出去的ByteBuf个数
            int free = size - allocations;
            allocations = 0;

            // We not even allocated all the number that are
            //todo    在一定阈值内还没被分配出去的空间将被释放
            if (free > 0) {
                //todo 释放队列中的节点
                free(free, false);
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private  void freeEntry(Entry entry, boolean finalizer) {
            PoolChunk chunk = entry.chunk;
            long handle = entry.handle;
            ByteBuffer nioBuffer = entry.nioBuffer;

            if (!finalizer) {
                // recycle now so PoolChunk can be GC'ed. This will only be done if this is not freed because of
                // a finalizer.
                entry.recycle();
            }

            chunk.arena.freeChunk(chunk, handle, sizeClass, nioBuffer, finalizer);
        }

        static final class  Entry<T> {
            //todo  Recycler 处理器，用于回收 Entry 对象
            final Handle<Entry<?>> recyclerHandle;

            //todo PoolChunk 对象
            PoolChunk<T> chunk;
            ByteBuffer nioBuffer;

            //todo 内存块在 {@link #chunk} 的位置
            long handle = -1;

            Entry(Handle<Entry<?>> recyclerHandle) {
                this.recyclerHandle = recyclerHandle;
            }

            void recycle() {
                chunk = null;
                nioBuffer = null;
                handle = -1;
                //todo 压入对象池
                recyclerHandle.recycle(this);
            }
        }



        //todo  从 Recycler 对象中，获得 Entry 对象
        private static Entry newEntry(PoolChunk<?> chunk, ByteBuffer nioBuffer, long handle) {
            Entry entry = RECYCLER.get();
            entry.chunk = chunk;
            entry.nioBuffer = nioBuffer;
            entry.handle = handle;
            return entry;
        }

        @SuppressWarnings("rawtypes")
        private static final Recycler<Entry> RECYCLER = new Recycler<Entry>() {
            @SuppressWarnings("unchecked")
            @Override
            protected Entry newObject(Handle<Entry> handle) {
                return new Entry(handle);
            }
        };
    }
}
