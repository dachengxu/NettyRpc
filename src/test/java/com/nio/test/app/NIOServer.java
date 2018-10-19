package com.nio.test.app;

/**
 * Author: xudacheng@patsnap.com
 * Date:   10/18/18 6:03 PM
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NIOServer {
    private Selector selector;          //创建一个选择器
    private final static int port = 8686;
    private final static int BUF_SIZE = 1024;

    private void initServer() throws IOException {
        //创建通道管理器对象selector
        this.selector = Selector.open();

        //创建一个通道对象channel
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);       //将通道设置为非阻塞
        channel.socket().bind(new InetSocketAddress(port));       //将通道绑定在8686端口

        //将上述的通道管理器和通道绑定，并为该通道注册OP_ACCEPT事件
        //注册事件后，当该事件到达时，selector.select()会返回（一个key），如果该事件没到达selector.select()会一直阻塞
        SelectionKey selectionKey = channel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Wait...");
        while (true) {       //轮询
            selector.select();          //这是一个阻塞方法，一直等待直到有数据可读，返回值是key的数量（可以有多个）
            Set keys = selector.selectedKeys();         //如果channel有数据了，将生成的key访入keys集合中
            Iterator iterator = keys.iterator();        //得到这个keys集合的迭代器
            while (iterator.hasNext()) {             //使用迭代器遍历集合
                SelectionKey key = (SelectionKey) iterator.next();       //得到集合中的一个key实例
                iterator.remove();          //拿到当前key实例之后记得在迭代器中将这个元素删除，非常重要，否则会出错
                if (key.isAcceptable()) {         //判断当前key所代表的channel是否在Acceptable状态，如果是就进行接收
                    System.out.println("Acceptable:  --> " + key.channel());
                    doAccept(key);
                } else if (key.isReadable()) {
                    System.out.println("Readable:  --> " + key.channel());
                    doRead(key);
                } else if (key.isWritable() && key.isValid()) {
                    System.out.println("Writeable:  --> " + key.channel());
                    doWrite(key);
                }
            }
        }
    }

    public void doAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(key.selector(), SelectionKey.OP_READ);
    }

    public void doRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUF_SIZE);
        long bytesRead = clientChannel.read(byteBuffer);
        while (bytesRead > 0) {
            byteBuffer.flip();

            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data, 0, data.length);

            String info = new String(data).trim();
            System.out.println("Recv：" + info);

            byteBuffer.clear();
            bytesRead = clientChannel.read(byteBuffer);
        }

        if (bytesRead == -1 || bytesRead == 0) {
            ByteBuffer outBuffer = ByteBuffer.wrap(new String("world...").getBytes());
            clientChannel.write(outBuffer);

            clientChannel.close();
        }
    }

    public void doWrite(SelectionKey key) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUF_SIZE);
        byteBuffer.flip();
        SocketChannel clientChannel = (SocketChannel) key.channel();
        while (byteBuffer.hasRemaining()) {
            clientChannel.write(byteBuffer);
        }
        byteBuffer.compact();
    }

    public static void main(String[] args) throws IOException {
        NIOServer server = new NIOServer();
        server.initServer();
    }
}