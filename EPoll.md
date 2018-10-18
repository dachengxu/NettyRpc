
ref: https://segmentfault.com/a/1190000003063859

缓存 I/O

缓存 I/O 又被称作标准 I/O，大多数文件系统的默认 I/O 操作都是缓存 I/O。
在 Linux 的缓存 I/O 机制中，操作系统会将 I/O 的数据缓存在文件系统的页缓存（ page cache ）中，
也就是说，数据会先被拷贝到操作系统内核的缓冲区中，然后才会从操作系统内核的缓冲区拷贝到应用程序的地址空间。

缓存 I/O 的缺点：
数据在传输过程中需要在应用程序地址空间和内核进行多次数据拷贝操作，这些数据拷贝操作所带来的 CPU 以及内存开销是非常大的。


### epoll接口
epoll操作过程需要三个接口，分别如下：

    int epoll_create(int size)；//创建一个epoll的句柄，size用来告诉内核这个监听的数目一共有多大
    int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event)；
    int epoll_wait(int epfd, struct epoll_event * events, int maxevents, int timeout);

1. int epoll_create(int size);
创建一个epoll的句柄，size用来告诉内核这个监听的数目一共有多大，
这个参数不同于select()中的第一个参数，给出最大监听的fd+1的值，
参数size并不是限制了epoll所能监听的描述符最大个数，只是对内核初始分配内部数据结构的一个建议。
当创建好epoll句柄后，它就会占用一个fd值，在linux下如果查看/proc/进程id/fd/，是能够看到这个fd的，
所以在使用完epoll后，必须调用close()关闭，否则可能导致fd被耗尽。

2. int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event)；
函数是对指定描述符fd执行op操作。
- epfd：是epoll_create()的返回值。
- op：表示op操作，用三个宏来表示：添加EPOLL_CTL_ADD，删除EPOLL_CTL_DEL，修改EPOLL_CTL_MOD。分别添加、删除和修改对fd的监听事件。
- fd：是需要监听的fd（文件描述符）
- epoll_event：是告诉内核需要监听什么事，struct epoll_event结构如下：

struct epoll_event {
  __uint32_t events;  /* Epoll events */
  epoll_data_t data;  /* User data variable */
};

//events可以是以下几个宏的集合：
EPOLLIN ：表示对应的文件描述符可以读（包括对端SOCKET正常关闭）；
EPOLLOUT：表示对应的文件描述符可以写；
EPOLLPRI：表示对应的文件描述符有紧急的数据可读（这里应该表示有带外数据到来）；
EPOLLERR：表示对应的文件描述符发生错误；
EPOLLHUP：表示对应的文件描述符被挂断；
EPOLLET： 将EPOLL设为边缘触发(Edge Triggered)模式，这是相对于水平触发(Level Triggered)来说的。
EPOLLONESHOT：只监听一次事件，当监听完这次事件之后，如果还需要继续监听这个socket的话，需要再次把这个socket加入到EPOLL队列里

3. int epoll_wait(int epfd, struct epoll_event * events, int maxevents, int timeout);
等待epfd上的io事件，最多返回maxevents个事件。
参数events用来从内核得到事件的集合，
maxevents告之内核这个events有多大，这个maxevents的值不能大于创建epoll_create()时的size，
参数timeout是超时时间（毫秒，0会立即返回，-1将不确定，也有说法说是永久阻塞）。
该函数返回需要处理的事件数目，如返回0表示已超时。



#### epoll触发方式
epoll提供edge-triggered及level-triggered模式。
在edge-trigger(ET)模式下，epoll_wait仅会在新的事件首次被加入epoll 对象时返回；
在level-triggered(LT)模式下，epoll_wait在事件状态未变更前将不断被触发。
epoll的默认模式是LT．


举例来说，倘若有一个已经于epoll注册之管线接获数据，epoll_wait将返回，并发出数据读取的信号。
现假设缓冲器的数据仅有部分被读取并处理，
在level-triggered模式下，任何对epoll_wait之调用都将即刻返回，直到缓冲器中的数据全部被读取；
然而，在edge-triggered的情境下，epoll_wait仅会于再次接收到新数据(亦即，新数据被写入管线)时返回。 

#### epoll示例代码
```cpp
#define MAX_EVENTS 10
struct epoll_event ev, events[MAX_EVENTS];
int listen_sock, conn_sock, nfds, epollfd;

/* Code to set up listening socket, 'listen_sock',
  (socket(), bind(), listen()) omitted */

epollfd = epoll_create1(0);
if (epollfd == -1) {
   perror("epoll_create1");
   exit(EXIT_FAILURE);
}

ev.events = EPOLLIN;
ev.data.fd = listen_sock;
if (epoll_ctl(epollfd, EPOLL_CTL_ADD, listen_sock, &ev) == -1) {
   perror("epoll_ctl: listen_sock");
   exit(EXIT_FAILURE);
}

for (;;) {
   nfds = epoll_wait(epollfd, events, MAX_EVENTS, -1);
   if (nfds == -1) {
       perror("epoll_wait");
       exit(EXIT_FAILURE);
   }

   for (n = 0; n < nfds; ++n) {
       if (events[n].data.fd == listen_sock) {
           conn_sock = accept(listen_sock,
                           (struct sockaddr *) &local, &addrlen);
           if (conn_sock == -1) {
               perror("accept");
               exit(EXIT_FAILURE);
           }
           setnonblocking(conn_sock);
           ev.events = EPOLLIN | EPOLLET;
           ev.data.fd = conn_sock;
           if (epoll_ctl(epollfd, EPOLL_CTL_ADD, conn_sock,
                       &ev) == -1) {
               perror("epoll_ctl: conn_sock");
               exit(EXIT_FAILURE);
           }
       } else {
           do_use_fd(events[n].data.fd);
       }
   }
}
```

#### epoll实现原理

1.执行epoll_create时，创建了红黑树和就绪list链表。

2.执行epoll_ctl时，如果增加fd（socket），则检查在红黑树中是否存在，存在立即返回，不存在则添加到红黑树上，
然后向内核注册回调函数，用于当中断事件来临时向准备就绪list链表中插入数据。

3.执行epoll_wait时立刻返回准备就绪链表里的数据即可。

