package test;



import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class Test {



    public static void main(String[] args) throws InterruptedException {


//        allocatorPageTest();

        allocatorTinyTest();


    }




    private static void allocatorTinyTest() {
        PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;

        allocator.directBuffer(16 * 1024 *1024 -1);


//        int tiny =  8;
//        int smal =  512;

//        System.out.println("tiny  size : " + tiny );
//        allocator.directBuffer(tiny);
//
//
//        System.out.println("tiny size : " + 2* tiny );
//        allocator.directBuffer(2* tiny);
//
//
//        System.out.println("tiny size : " + 4 * tiny );
//        allocator.directBuffer(4 * tiny);
//
//
//
//        System.out.println("smal  size : " + smal );
//        allocator.directBuffer( smal);
//
//        System.out.println("smal  size : " + 2 * smal );
//        allocator.directBuffer( 2 * smal);
//
//
//        System.out.println("smal  size : " + 3* smal );
//        allocator.directBuffer( 3 *  smal);
//
//        System.out.println("smal  size : " + 4* smal );
//        allocator.directBuffer( 4 *  smal);
//
//        System.out.println("smal  size : " + 6* smal );
//        allocator.directBuffer( 6 *  smal);
//
//        System.out.println("smal  size : " + 8* smal );
//        allocator.directBuffer( 8 *  smal);
//
//
//        System.out.println("normal  size : " + 6*1024 );
//        allocator.directBuffer( 6*1024);
//
//        System.out.println("normal  size : " + 8*1024 );
//        allocator.directBuffer( 8*1024);
//
//
//        System.out.println("normal  size : " + 12*1024 );
//        allocator.directBuffer( 12*1024);

    }


    private static void allocatorPageTest() {
        int page = 1024 * 8;
        PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        System.out.println("============ start ==============");
        allocator.directBuffer(2*page);
        System.out.println("=============   1   =============");
        allocator.directBuffer(2*page);
        System.out.println("=============   2   =============");
        allocator.directBuffer(2*page);


        System.out.println("=============   3   =============");
        allocator.directBuffer(page);

        System.out.println("=============   end ==============");

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
