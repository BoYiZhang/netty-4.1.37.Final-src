//package io.netty.study.ch9;
//
//import io.netty.buffer.ByteBuf;
//import io.netty.channel.ChannelHandlerContext;
//
///**
// * ---------------------
// *|   4    |  4  |  ?   |
// * ---------------------
// *| length | age | name |
// * ---------------------
// */
//
//
//
//public class Encoder extends MessageToByteEncoder<User> {
//    @Override
//    protected void encode(ChannelHandlerContext ctx, User user, ByteBuf out) throws Exception {
//
//        byte[] bytes = user.getName().getBytes();
//        out.writeInt(4 + bytes.length);
//        out.writeInt(user.getAge());
//        out.writeBytes(bytes);
//    }
//}
