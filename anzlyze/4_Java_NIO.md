
## Java NIO
ref: 
https://mp.weixin.qq.com/s/c9tkrokcDQR375kiwCeV9w?

http://ifeve.com/java-nio-all/

NIO主要有三大核心部分：Channel(通道)，Buffer(缓冲区), Selector。传统IO基于字节流和字符流进行操作，而NIO基于Channel和Buffer(缓冲区)进行操作，数据总是从通道读取到缓冲区中，或者从缓冲区写入到通道中。Selector(选择区)用于监听多个通道的事件（比如：连接打开，数据到达）。因此，单个线程可以监听多个数据通道。

NIO和传统IO（一下简称IO）之间第一个最大的区别是，IO是面向流的，NIO是面向缓冲区的。 Java IO面向流意味着每次从流中读一个或多个字节，直至读取所有字节，它们没有被缓存在任何地方。此外，它不能前后移动流中的数据。如果需要前后移动从流中读取的数据，需要先将它缓存到一个缓冲区。NIO的缓冲导向方法略有不同。数据读取到一个它稍后处理的缓冲区，需要时可在缓冲区中前后移动。这就增加了处理过程中的灵活性。但是，还需要检查是否该缓冲区中包含所有您需要处理的数据。而且，需确保当更多的数据读入缓冲区时，不要覆盖缓冲区里尚未处理的数据。

IO的各种流是阻塞的。这意味着，当一个线程调用read() 或 write()时，该线程被阻塞，直到有一些数据被读取，或数据完全写入。该线程在此期间不能再干任何事情了。 NIO的非阻塞模式，使一个线程从某通道发送请求读取数据，但是它仅能得到目前可用的数据，如果目前没有数据可用时，就什么都不会获取。而不是保持线程阻塞，所以直至数据变得可以读取之前，该线程可以继续做其他的事情。 非阻塞写也是如此。一个线程请求写入一些数据到某通道，但不需要等待它完全写入，这个线程同时可以去做别的事情。 线程通常将非阻塞IO的空闲时间用于在其它通道上执行IO操作，所以一个单独的线程现在可以管理多个输入和输出通道（channel）。


### Channel

Channel和IO中的Stream(流)是差不多一个等级的。只不过Stream是单向的，譬如：InputStream, OutputStream.而Channel是双向的，既可以用来进行读操作，又可以用来进行写操作。
NIO中的Channel的主要实现有：

    FileChannel
    DatagramChannel
    SocketChannel
    ServerSocketChannel

这里看名字就可以猜出个所以然来：分别可以对应文件IO、UDP和TCP（Server和Client）。

### Buffer

NIO中的关键Buffer实现有：ByteBuffer, CharBuffer, DoubleBuffer, FloatBuffer, IntBuffer, LongBuffer, ShortBuffer，分别对应基本数据类型: byte, char, double, float, int, long, short。当然NIO中还有MappedByteBuffer, HeapByteBuffer, DirectByteBuffer等这里先不进行陈述。

### Selector

Selector运行单线程处理多个Channel，如果你的应用打开了多个通道，但每个连接的流量都很低，使用Selector就会很方便。例如在一个聊天服务器中。要使用Selector, 得向Selector注册Channel，然后调用它的select()方法。这个方法会一直阻塞到某个注册的通道有事件就绪。一旦这个方法返回，线程就可以处理这些事件，事件的例子有如新的连接进来、数据接收等。

#### 创建选择器
通过 Selector.open()方法, 我们可以创建一个选择器:

    Selector selector = Selector.open();
    
将 Channel 注册到选择器中
为了使用选择器管理 Channel, 我们需要将 Channel 注册到选择器中:

    channel.configureBlocking(false);
    SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
    
注意, 如果一个 Channel 要注册到 Selector 中, 那么这个 Channel 必须是**非阻塞**的, 即channel.configureBlocking(false);
>因为 Channel 必须要是非阻塞的, 因此 FileChannel 是不能够使用选择器的, 因为 FileChannel 都是阻塞的.

注意到, 在使用 Channel.register()方法时, 第二个参数指定了我们对 Channel 的什么类型的事件感兴趣, 这些事件有:

Connect, 即连接事件(TCP 连接), 对应于SelectionKey.OP_CONNECT
Accept, 即确认事件, 对应于SelectionKey.OP_ACCEPT
Read, 即读事件, 对应于SelectionKey.OP_READ, 表示 buffer 可读.
Write, 即写事件, 对应于SelectionKey.OP_WRITE, 表示 buffer 可写.

一个 Channel发出一个事件也可以称为 对于某个事件, Channel 准备好了. 因此一个 Channel 成功连接到了另一个服务器也可以被称为 connect ready.
我们可以使用或运算|来组合多个事件, 例如:

int interestSet = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
注意, 一个 Channel 仅仅可以被注册到一个 Selector 一次, 如果将 Channel 注册到 Selector 多次, 那么其实就是相当于更新 SelectionKey 的 interest set. 例如:

channel.register(selector, SelectionKey.OP_READ);
channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
上面的 channel 注册到同一个 Selector 两次了, 那么第二次的注册其实就是相当于更新这个 Channel 的 interest set 为 SelectionKey.OP_READ | SelectionKey.OP_WRITE.

### 示例：　FileChannel

传统IO vs NIO

首先，案例1是采用FileInputStream读取文件内容的：

    public static void method2(){
        InputStream in = null;
        try{
            in = new BufferedInputStream(new FileInputStream("src/nomal_io.txt"));
            byte [] buf = new byte[1024];
            int bytesRead = in.read(buf);
            while(bytesRead != -1)
            {
                for(int i=0;i<bytesRead;i++)
                    System.out.print((char)buf[i]);
                bytesRead = in.read(buf);
            }
        }catch (IOException e)
        {
            e.printStackTrace();
        }finally{
            try{
                if(in != null){
                    in.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }


案例2是对应的NIO（这里通过RandomAccessFile进行操作，当然也可以通过FileInputStream.getChannel()进行操作）：

    public static void method1(){
        RandomAccessFile aFile = null;
        try{
            aFile = new RandomAccessFile("src/nio.txt","rw");
            FileChannel fileChannel = aFile.getChannel();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            int bytesRead = fileChannel.read(buf);
            System.out.println(bytesRead);
            while(bytesRead != -1)
            {
                buf.flip();
                while(buf.hasRemaining())
                {
                    System.out.print((char)buf.get());
                }
                buf.compact();
                bytesRead = fileChannel.read(buf);
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally{
            try{
                if(aFile != null){
                    aFile.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

通过仔细对比案例1和案例2，应该能看出个大概，最起码能发现NIO的实现方式比叫复杂。有了一个大概的印象可以进入下一步了。


### SocketChannel

NIO的强大功能部分来自于Channel的非阻塞特性，套接字的某些操作可能会无限期地阻塞。例如，对accept()方法的调用可能会因为等待一个客户端连接而阻塞；对read()方法的调用可能会因为没有数据可读而阻塞，直到连接的另一端传来新的数据。总的来说，创建/接收连接或读写数据等I/O调用，都可能无限期地阻塞等待，直到底层的网络实现发生了什么。NIO的channel抽象的一个重要特征就是可以通过配置它的阻塞行为，以实现非阻塞式的信道。

            channel.configureBlocking(false)

在非阻塞式信道上调用一个方法总是会立即返回。这种调用的返回值指示了所请求的操作完成的程度。例如，在一个非阻塞式ServerSocketChannel上调用accept()方法，如果有连接请求来了，则返回客户端SocketChannel，否则返回null。

### 示例：　ServerSocketChannel


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
            //SelectionKey.OP_ACCEPT:连接可接受操作,仅ServerSocketChannel支持
            //SelectionKey.OP_CONNECT:连接操作,Client端支持的一种操作
            //SelectionKey.OP_READ —— 读就绪事件，表示通道中已经有了可读的数据，可以执行读操作了（通道目前有数据，可以进行读操作了）
            //SelectionKey.OP_WRITE —— 写就绪事件，表示已经可以向通道写数据了（通道目前可以用于写操作）
            
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

### 示例：　SocketChannel
    
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
