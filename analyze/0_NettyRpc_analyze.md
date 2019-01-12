

##Server

####加载spring配置文件server-spring.xml
```java
public class RpcBootstrap {

    public static void main(String[] args) {
        new ClassPathXmlApplicationContext("server-spring.xml");
    }
}
```

#### Spring容器启动后依次调用 RpcServer#setApplicationContext, RpcServer#afterPropertiesSet方法
- setApplicationContext方法会把所有标注了RpcService注解的实例加入到handlerMap中:
例如："com.nettyrpc.test.client.HelloService" -> HelloServiceImpl, 
"com.nettyrpc.test.client.PersonService" -> PersonServiceImpl


```java
public class RpcServer implements AppliparameterTypescationContextAware, InitializingBean {
    
    private Map<String, Object> handlerMap = new HashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                String interfaceName = serviceBean.getClass().getAnnotation(RpcService.class).value().getName();
                logger.info("Loading service: {}", interfaceName);
                handlerMap.put(interfaceName, serviceBean);
            }
        }
    }
}
```

- afterPropertiesSet调用start方法
1. 在start方法中启动netty-server
2. **使用ZooKeeper发布netty-server的地址**

```java
public class RpcServer implements ApplicationContextAware, InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }
        
    public void start() throws Exception {
            if (bossGroup == null && workerGroup == null) {
                bossGroup = new NioEventLoopGroup();
                workerGroup = new NioEventLoopGroup();
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel channel) throws Exception {
                                channel.pipeline()
                                        .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0))
                                        .addLast(new RpcDecoder(RpcRequest.class))
                                        .addLast(new RpcEncoder(RpcResponse.class))
                                        .addLast(new RpcHandler(handlerMap));
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);
    
                String[] array = serverAddress.split(":");
                String host = array[0];
                int port = Integer.parseInt(array[1]);
    
                ChannelFuture future = bootstrap.bind(host, port).sync();
                logger.info("Server started on port {}", port);
    
                if (serviceRegistry != null) {
                    serviceRegistry.register(serverAddress);//发布netty-server地址
                }
    
                future.channel().closeFuture().sync();
            }
        }
}
```

#### LengthFieldBasedFrameDecoder
主要用于处理黏包和半包消息．　只要传入正确的参数，就可以轻松解决“读半包”的问题。
1) lengthFieldOffset = 0；//长度字段的偏差
2) lengthFieldLength = 4；//长度字段占的字节数
3) lengthAdjustment = 0；//添加到长度字段的补偿值
4) initialBytesToStrip = 0。//从解码帧中第一次去除的字节数

因此，rpc数据包解码后格式为： 4字节标识rpc消息长度+rpc消息

    　     +++++++++++++++++++++++++++++++++++++
         |   Length  |        Content          |
          ++++++++++++++++++++++++++++++++++++++
https://blog.csdn.net/u010853261/article/details/55803933

Netty提供了多个解码器，可以进行分包的操作，分别是： 
* LineBasedFrameDecoder (回车换行解码器)
* DelimiterBasedFrameDecoder（分隔符解码器，用户可以指定消息结束的分隔符） 
* FixedLengthFrameDecoder（使用定长的报文来分包） 
* LengthFieldBasedFrameDecoder　（基于长度解码）

#### RpcDecoder用于解包
把rpc消息返序列化成RpcRequest实例．
首先获取消息长度，之后再根据消息长度读取指定的字节进行反序列化成．
```java
public class RpcDecoder extends ByteToMessageDecoder {

    private Class<?> genericClass;

    public RpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    public final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();
        int dataLength = in.readInt();//rpc消息长度
        /*if (dataLength <= 0) {
            ctx.close();
        }*/
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        Object obj = SerializationUtil.deserialize(data, genericClass);
        //Object obj = JsonUtil.deserialize(data,genericClass); // Not use this, have some bugs
        out.add(obj);
    }

}
```

####RpcHandler实现具体方法调用
handlerMap是RpcServer中获取到的实现类Map, handle方法通过反射调用具体方法，这里使用了线程池异步处理rpc请求．

```java
public class RpcHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private final Map<String, Object> handlerMap;

    public RpcHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx,final RpcRequest request) throws Exception {
        RpcServer.submit(new Runnable() {//使用新线程，异步处理rpc请求
            @Override
            public void run() {
                logger.debug("Receive request " + request.getRequestId());
                RpcResponse response = new RpcResponse();
                response.setRequestId(request.getRequestId());
                try {
                    Object result = handle(request);
                    response.setResult(result);
                } catch (Throwable t) {
                    response.setError(t.toString());
                    logger.error("RPC Server handle request error",t);
                }
                ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        logger.debug("Send response for request " + request.getRequestId());
                    }
                });//执行结束，发送RpcResponse结果
            }
        });
    }

    private Object handle(RpcRequest request) throws Throwable {
        String className = request.getClassName();
        Object serviceBean = handlerMap.get(className);//获取到具体实现类

        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        logger.debug(serviceClass.getName());
        logger.debug(methodName);
        for (int i = 0; i < parameterTypes.length; ++i) {
            logger.debug(parameterTypes[i].getName());
        }
        for (int i = 0; i < parameters.length; ++i) {
            logger.debug(parameters[i].toString());
        }

        // JDK reflect
        /*Method method = serviceClass.getMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(serviceBean, parameters);*/

        // Cglib reflect
        FastClass serviceFastClass = FastClass.create(serviceClass);
        FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
        return serviceFastMethod.invoke(serviceBean, parameters);//调用具体方法
    }
}
```



##Client

示例代码如下：
1) 首先新建ServiceDiscovery实例，构造函数中会进行服务发现
2) 新建RpcClient，调用createAsync新建一个代理；
3) 通过代理发起异步rpc请求
```java
public class PersonCallbackTest {
    public static void main(String[] args) {
        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181");
        final RpcClient rpcClient = new RpcClient(serviceDiscovery);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        try {
            IAsyncObjectProxy client = rpcClient.createAsync(PersonService.class);
            int num = 5;
            RPCFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);
            helloPersonFuture.addCallback(new AsyncRPCCallback() {
                @Override
                public void success(Object result) {
                    List<Person> persons = (List<Person>) result;
                    for (int i = 0; i < persons.size(); ++i) {
                        System.out.println(persons.get(i));
                    }
                    countDownLatch.countDown();
                }

                @Override
                public void fail(Exception e) {
                    System.out.println(e);
                    countDownLatch.countDown();
                }
            });

        } catch (Exception e) {
            System.out.println(e);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        rpcClient.stop();

        System.out.println("End");
    }
}
```

####新建ServiceDiscovery实例

        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181");//127.0.0.1:2181为zk的地址


ServiceDiscovery构造方法中调用　connectServer　和　watchNode方法
```java
public class ServiceDiscovery {

    public ServiceDiscovery(String registryAddress) {
        this.registryAddress = registryAddress;
        zookeeper = connectServer();
        if (zookeeper != null) {
            watchNode(zookeeper);
        }
    }
}
```

connectServer用于发起ZooKeeper连接，直到连接成功后返回
```java
public class ServiceDiscovery {
    private CountDownLatch latch = new CountDownLatch(1);
    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(registryAddress, Constant.ZK_SESSION_TIMEOUT, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        latch.countDown();//连接成功
                    }
                }
            });
            latch.await();
        } catch (IOException | InterruptedException e) {
            logger.error("", e);
        }
        return zk;
    }
}
```


watchNode获取zk指定结点下的所有儿子结点，并留下监视器监听儿子结点的变化.
这些儿子结点存储的数据为netty-server的地址.
调用ConnectManage.getInstance().updateConnectedServer方法．
```java
public class ServiceDiscovery {
    private void watchNode(final ZooKeeper zk) {
        try {
            List<String> nodeList = zk.getChildren(Constant.ZK_REGISTRY_PATH, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getType() == Event.EventType.NodeChildrenChanged) {
                        watchNode(zk);
                    }
                }
            });
            List<String> dataList = new ArrayList<>();
            for (String node : nodeList) {
                byte[] bytes = zk.getData(Constant.ZK_REGISTRY_PATH + "/" + node, false, null);
                dataList.add(new String(bytes));
            }
            logger.debug("node data: {}", dataList);
            this.dataList = dataList;

            logger.debug("Service discovery triggered updating connected server node.");
            UpdateConnectedServer();
        } catch (KeeperException | InterruptedException e) {
            logger.error("", e);
        }
    }
    
    private void UpdateConnectedServer(){
            ConnectManage.getInstance().updateConnectedServer(this.dataList);
        }
}
```

####ConnectManage#updateConnectedServer
ConnectManage是一个单件，主要负责rpc连接管理

updateConnectedServer根据netty-server地址，创建或删除到netty-server的连接
```java
public class ConnectManage {
    private CopyOnWriteArrayList<RpcClientHandler> connectedHandlers = new CopyOnWriteArrayList<>();
    private Map<InetSocketAddress, RpcClientHandler> connectedServerNodes = new ConcurrentHashMap<>();


    public void updateConnectedServer(List<String> allServerAddress) {
        if (allServerAddress != null) {
            if (allServerAddress.size() > 0) {  // Get available server node
                //update local serverNodes cache
                HashSet<InetSocketAddress> newAllServerNodeSet = new HashSet<InetSocketAddress>();
                for (int i = 0; i < allServerAddress.size(); ++i) {
                    String[] array = allServerAddress.get(i).split(":");
                    if (array.length == 2) { // Should check IP and port
                        String host = array[0];
                        int port = Integer.parseInt(array[1]);
                        final InetSocketAddress remotePeer = new InetSocketAddress(host, port);
                        newAllServerNodeSet.add(remotePeer);
                    }
                }

                // Add new server node
                for (final InetSocketAddress serverNodeAddress : newAllServerNodeSet) {
                    if (!connectedServerNodes.keySet().contains(serverNodeAddress)) {
                        connectServerNode(serverNodeAddress);
                    }
                }

                // Close and remove invalid server nodes
                for (int i = 0; i < connectedHandlers.size(); ++i) {
                    RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    if (!newAllServerNodeSet.contains(remotePeer)) {
                        logger.info("Remove invalid server node " + remotePeer);
                        RpcClientHandler handler = connectedServerNodes.get(remotePeer);
                        if (handler != null) {
                            handler.close();
                        }
                        connectedServerNodes.remove(remotePeer);
                        connectedHandlers.remove(connectedServerHandler);
                    }
                }

            } else { // No available server node ( All server nodes are down )
                logger.error("No available server node. All server nodes are down !!!");
                for (final RpcClientHandler connectedServerHandler : connectedHandlers) {
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    RpcClientHandler handler = connectedServerNodes.get(remotePeer);
                    handler.close();
                    connectedServerNodes.remove(connectedServerHandler);
                }
                connectedHandlers.clear();
            }
        }
    }}
```

connectServerNode创建netty客户端，并且把RpcClientHandler加到列表中保存，通知正在等待handler的列表
```java
public class ConnectManage {
    private void connectServerNode(final InetSocketAddress remotePeer) {
        threadPoolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Bootstrap b = new Bootstrap();
                b.group(eventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcClientInitializer());

                ChannelFuture channelFuture = b.connect(remotePeer);
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            logger.debug("Successfully connect to remote server. remote peer = " + remotePeer);
                            RpcClientHandler handler = channelFuture.channel().pipeline().get(RpcClientHandler.class);
                            addHandler(handler);
                        }
                    }
                });
            }
        });
    }
    
    private void addHandler(RpcClientHandler handler) {
        connectedHandlers.add(handler);
        InetSocketAddress remoteAddress = (InetSocketAddress) handler.getChannel().remoteAddress();
        connectedServerNodes.put(remoteAddress, handler);
        signalAvailableHandler();
    }
}
```

####RpcClientInitializer类中包含了基本的Handler
- RpcEncoder把RpcRequest进行序列化用于传输
- LengthFieldBasedFrameDecoder用于解包
- RpcDecoder用于把消息解包成RpcResponse
- RpcClientHandler用于客户端发送请求，获取结果
```java
public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline cp = socketChannel.pipeline();
        cp.addLast(new RpcEncoder(RpcRequest.class));
        cp.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0));
        cp.addLast(new RpcDecoder(RpcResponse.class));
        cp.addLast(new RpcClientHandler());
    }
}
```

RpcEncoder把RpcRequest进行序列化用于传输，前４个字节是序列化消息的长度
```java
public class RpcEncoder extends MessageToByteEncoder {

    private Class<?> genericClass;

    public RpcEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {
        if (genericClass.isInstance(in)) {
            byte[] data = SerializationUtil.serialize(in);
            //byte[] data = JsonUtil.serialize(in); // Not use this, have some bugs
            out.writeInt(data.length);//前４个字节是序列化消息的升序
            out.writeBytes(data);
        }
    }
}
```

####RpcClient创建

        final RpcClient rpcClient = new RpcClient(serviceDiscovery);
        IAsyncObjectProxy client = rpcClient.createAsync(PersonService.class);

createAsync用于新建一个接口的代理ObjectProxy
```java
public class RpcClient {
    public static <T> IAsyncObjectProxy createAsync(Class<T> interfaceClass) {
        return new ObjectProxy<T>(interfaceClass);
    }
}
```

通过client发送rpc调用

        RPCFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);


ObjectProxy#call方法获取可用RpcClientHandler, 创建RpcRequest，发送rpc请求
```java
public class ObjectProxy<T> implements InvocationHandler, IAsyncObjectProxy {
    @Override
    public RPCFuture call(String funcName, Object... args) {
        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RpcRequest request = createRequest(this.clazz.getName(), funcName, args);
        RPCFuture rpcFuture = handler.sendRequest(request);//发送rpc请求
        return rpcFuture;
    }}
```

chooseHandler在可用RpcClientHandler为０时会等待，如果在等待过程中被唤醒再次查看可用RpcClientHandler，直到size　>　0; 
之后，做一个roundRobin挑选一个RpcClientHandler返回．
```java
public class ConnectManage {
    public RpcClientHandler chooseHandler() {
        int size = connectedHandlers.size();
        while (isRuning && size <= 0) {
            try {
                boolean available = waitingForHandler();
                if (available) {
                    size = connectedHandlers.size();
                }
            } catch (InterruptedException e) {
                logger.error("Waiting for available node is interrupted! ", e);
                throw new RuntimeException("Can't connect any servers!", e);
            }
        }
        int index = (roundRobin.getAndAdd(1) + size) % size;
        return connectedHandlers.get(index);
    }}
```

####RpcClientHandler#sendRequest方法
新建一个RpcFuture与RpcRequest绑定放入到pendingRPC中，发送RpcRequest请求．
channelRead0方法会在收到响应时调用RpcFuure的done方法．
RpcFuture是一个Future的实现类．

```java

public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    private ConcurrentHashMap<String, RPCFuture> pendingRPC = new ConcurrentHashMap<>();

    private volatile Channel channel;
    private SocketAddress remotePeer;

    public Channel getChannel() {
        return channel;
    }

    public SocketAddress getRemotePeer() {
        return remotePeer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.remotePeer = this.channel.remoteAddress();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        this.channel = ctx.channel();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        String requestId = response.getRequestId();
        RPCFuture rpcFuture = pendingRPC.get(requestId);
        if (rpcFuture != null) {
            pendingRPC.remove(requestId);
            rpcFuture.done(response);//保存结果
        }
    }

    public RPCFuture sendRequest(RpcRequest request) {
        final CountDownLatch latch = new CountDownLatch(1);
        RPCFuture rpcFuture = new RPCFuture(request);
        pendingRPC.put(request.getRequestId(), rpcFuture);//根据requestId保存
        channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }

        return rpcFuture;
    }
}
```

