package com.nio.test.app;

/**
 * Author: xudacheng@patsnap.com
 * Date:   10/18/18 6:04 PM
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIOClient {
    private Selector selector;          //创建一个选择器
    private final static int port = 8686;
    private final static int BUF_SIZE = 1024;
    private static ByteBuffer byteBuffer = ByteBuffer.allocate(BUF_SIZE);

    private void initClient() throws IOException {
        this.selector = Selector.open();
        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);
        clientChannel.connect(new InetSocketAddress(port));
        clientChannel.register(selector, SelectionKey.OP_CONNECT);
        while (true) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isConnectable()) {
                    System.out.println("Connectable --> " + key.channel());
                    doConnect(key);
                } else if (key.isReadable()) {
                    System.out.println("Readable  --> " + key.channel());
                    doRead(key);
                }
            }
        }
    }

    public void doConnect(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        if (clientChannel.isConnectionPending()) {
            clientChannel.finishConnect();
            System.out.println("Connectable --> " + clientChannel);
        }
        clientChannel.configureBlocking(false);
        String info = "服务端你好!!";
        byteBuffer.clear();
        byteBuffer.put(info.getBytes("UTF-8"));
        byteBuffer.flip();
        clientChannel.write(byteBuffer);
        clientChannel.register(key.selector(), SelectionKey.OP_READ);
        //clientChannel.close();
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
            ByteBuffer outBuffer = ByteBuffer.wrap(new String("hello...").getBytes());
            clientChannel.write(outBuffer);
        }

        clientChannel.close();
    }

    public static void main(String[] args) throws IOException {
        NIOClient client = new NIOClient();
        client.initClient();
    }
}
