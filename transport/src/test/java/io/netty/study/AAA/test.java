package io.netty.study.AAA;

import io.netty.util.NettyRuntime;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class test {

    Long INDEX = 10_0000L ;


    static final int defaultMinNumArena = NettyRuntime.availableProcessors() * 2;


    //todo default 8k
    static final int DEFAULT_PAGE_SIZE = 8192 ;


    //todo 满二叉树的高度，默认为 11 。
    static final int DEFAULT_MAX_ORDER = 11; // 8192 << 11 = 16 MiB per chunk


    static final int defaultChunkSize = DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER ;




    public static void main(String[] args)   {
//        String data = "AAAAAA";

//        data = data +"BBBBBB" ;

//        System.out.println(data);

//        System.out.println( (((long) Integer.MAX_VALUE + 1) ));

//        new test().test01();
//        new test().test02();
//        new test().test03();


        System.out.println(log2(8192));
//        System.out.println(PlatformDependent.maxDirectMemory());
//
//        System.out.println(PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3);
//
//        int  DEFAULT_NUM_DIRECT_ARENA = Math.max(0,
//                SystemPropertyUtil.getInt(
//                        "io.netty.allocator.numDirectArenas",
//                        (int) Math.min(
//                                defaultMinNumArena,
//                                PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3)));
//
//        System.out.println(DEFAULT_NUM_DIRECT_ARENA);

    }


    private static int log2(int val) {
        int res = 0;
        while (val > 1) {
            val >>= 1;
            res++;
        }
        return res;
    }


    public void test01(){

        Long  startTime = System.currentTimeMillis();

        String data = "" ;


        for (int i=0 ; i < INDEX ;i++) {
            data = data+"a" ;

        }


        System.out.println(data.length());

        Long endTime = System.currentTimeMillis();

        System.out.println(" test01 total time : " + (endTime-startTime));
    }


    public void test02(){
        Long  startTime = System.currentTimeMillis();

        StringBuffer sb = new StringBuffer();

        for (int i=0 ; i < INDEX ;i++) {
            sb.append("a")  ;

        }

        System.out.println(sb.length());


        Long endTime = System.currentTimeMillis();

        System.out.println(" test02 total time : " + (endTime-startTime));

    }


    public void test03(){

        Long  startTime = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        for (int i=0 ; i < INDEX ;i++) {
            sb.append("a")  ;

        }

        System.out.println(sb.length());


        Long endTime = System.currentTimeMillis();

        System.out.println(" test03 total time : " + (endTime-startTime));
    }


}
