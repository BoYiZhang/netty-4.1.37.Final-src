package test;



import io.netty.buffer.PooledByteBufAllocator;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class TestBinary {



    public static void main(String[] args) throws InterruptedException {
        //todo total 1677216

//        System.out.println((2<<15) / 1024);

        System.out.println(10/3);
        System.out.println(10L/3);
        System.out.println(10F/3);
        System.out.println(10D/3);


//         int HASH_BITS = 0x7fffffff; // usable bits of normal node hash
//
//        System.out.println(HASH_BITS);
//        System.out.println(Integer.MAX_VALUE);

//        ConcurrentHashMap hashMap = new ConcurrentHashMap();
//        hashMap.put("","1111");
////
//        System.out.println(hashMap.get(""));

//        System.out.println( 8 & 15-1);

//        System.out.println( 1 << 14 );
//        System.out.println( 1 << 24 );
//        System.out.println( 1 << 24 - 10 );
//
//
//        System.out.println(1677216 - (1 << 24 - 10)  );




//        System.out.println(Integer.toBinaryString(15));
//        System.out.println(Integer.toBinaryString(~15));
//        System.out.println(Integer.toBinaryString(8));
//        System.out.println(Integer.toBinaryString(8 & ~15));


//        System.out.println( 8192>>>10);

//        System.out.println( Integer.toBinaryString(512));
//        System.out.println( Integer.toBinaryString(63));


//        System.out.println(0x4000000000000000L);
//        System.out.println(Long.toBinaryString(4611686018427387904L));
//        System.out.println(Integer.toBinaryString(0 << 32));
//        System.out.println(Integer.toBinaryString(2048));
//
//        System.out.println(Long.toBinaryString((0x4000000000000000L | (long) 0 << 32 | 2048)));

//        0x4000000000000000L | (long) 0 << 32 | 2048


//        System.out.println(~0);



    }





    private static void testPoolChunk() {

        //todo 分配信息满二叉树
        byte[] memoryMap;
        //todo 高度信息满二叉树
        byte[] depthMap;

        int maxOrder = 11 ;
        int maxSubpageAllocs = 1 << maxOrder;

        //todo  初始化 memoryMap 和 depthMap
        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];

        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }



        StringBuilder indexMapBuff = new StringBuilder();
        StringBuilder memoryMapBuff = new StringBuilder();
        StringBuilder depthMapBuff = new StringBuilder();

        for (int i=0 ;  i < memoryMap.length ; i++  ) {

            indexMapBuff.append( i+"\t") ;
            memoryMapBuff.append( memoryMap[i]+"\t") ;
            depthMapBuff.append( depthMap[i]+"\t") ;

        }

        System.out.println(indexMapBuff.toString());
        System.out.println(memoryMapBuff.toString());
        System.out.println(depthMapBuff.toString());
    }






    static boolean isTiny(int normCapacity) {

        return (normCapacity & 0xFFFFFE00) == 0;
    }



}
