package casia.isiteam.FileTransfer.sender;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import casia.isiteam.FileTransfer.common.FileInfo;
import casia.isiteam.FileTransfer.common.ResultCode;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class FileSender extends Thread {
	String host;
	int fileTransferPort;
	Channel ch = null;
	EventExecutor executor = new DefaultEventExecutor();
	public EventExecutor getExecutor() {
		return executor;
	}

	ConcurrentLinkedQueue<Promise<ResultCode>> promiseQueue = new ConcurrentLinkedQueue<Promise<ResultCode>>();



	public FileSender(String host, int fileTransferPort) {
		this.host = host;
		this.fileTransferPort = fileTransferPort;
	}

	public Future<ResultCode> sendFile(final FileInfo fileInfo) {

		executor.submit(new Callable<Void>() {

			public Void call() throws Exception {
			
				byte[] b = fileInfo.getFileName().getBytes();					
				
				ByteBuf buf = ch.alloc().buffer();
				
							
				buf.writeInt(b.length);
				buf.writeBytes(fileInfo.getFileName().getBytes());
				buf.writeLong(fileInfo.getFileLength());
				
				ch.writeAndFlush(buf);				
				ch.writeAndFlush(new ChunkedFile(fileInfo.getFile()));

				return null;
			}
		});

		// 写完之后 ，应该收到回复才确认真的完成了。
		Promise<ResultCode> ret = executor.newPromise();

		promiseQueue.offer(ret);
		return ret;
	}

	public ConcurrentLinkedQueue<Promise<ResultCode>> getPromiseQueue() {
		return promiseQueue;
	}
	
	@Override
	public void run() {

		EventLoopGroup group = new NioEventLoopGroup();

		final FileSender fileSender = this;
		try {
			Bootstrap bootstrap = new Bootstrap();
			bootstrap.channel(NioSocketChannel.class);
			bootstrap.group(group);
			bootstrap.handler(new ChannelInitializer<SocketChannel>() {

				@Override
				protected void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline p = ch.pipeline();

					//decoder
					p.addLast(new LoggingHandler(LogLevel.INFO));
					p.addLast(new LengthFieldBasedFrameDecoder(
							Integer.MAX_VALUE, 0, Integer.BYTES, 0,
							Integer.BYTES));
					p.addLast(new StringDecoder());

					//encoder
					p.addLast(new LengthFieldPrepender(Integer.BYTES));
					p.addLast(new ChunkedWriteHandler());
					
					//business logic
					p.addLast(new FileSenderHandler(fileSender));
				}
			});

			ChannelFuture f = bootstrap.connect(host, fileTransferPort).sync();

			synchronized (this) {
				this.notify();
			}
			
			this.ch = f.channel();
			f.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {

		}
	}

	public static void main(String[] args) throws InterruptedException {

		FileSender fileSender = new FileSender("127.0.0.1", 9001);

		fileSender.start();

		synchronized (fileSender) {
			fileSender.wait();
		}
		
		final FileSender finalFileSender = fileSender;
		final File file = new File("C:/1.txt");
		int num = 20;
		for (int i = 0; i != num; ++i) {

			new Thread() {
				@Override
				public void run() {
				
					try {
						finalFileSender.sendFile(new FileInfo(file));
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			}.start();
		}
	}
}
