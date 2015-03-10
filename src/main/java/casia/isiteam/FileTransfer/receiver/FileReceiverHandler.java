package casia.isiteam.FileTransfer.receiver;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import casia.isiteam.FileTransfer.common.FileInfo;
import casia.isiteam.FileTransfer.common.ResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

public class FileReceiverHandler extends ChannelHandlerAdapter {

	FileInfo fileInfo = null;
	FileReceiver fileReceiver=null;
	OutputStream out=null;
	BufferedOutputStream bout=null;
	long readedSize=0;
	public FileReceiverHandler(FileReceiver fileReceiver){
		this.fileReceiver=fileReceiver;		
		
	}
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		ByteBuf buf = (ByteBuf) msg;

		if (fileInfo == null) {
			int fileNameLength = buf.readInt();

			byte[] dst = new byte[fileNameLength];

			buf.readBytes(dst);
			String nameOnClient = new String(dst, "UTF-8");
			long fileLength = buf.readLong();
			fileInfo = new FileInfo(nameOnClient, fileLength);
			out=new FileOutputStream(fileReceiver.getNameFileName(nameOnClient));
			bout=new BufferedOutputStream(out);
		}
		
		readedSize+=buf.readableBytes();
		
		byte[] b=new byte[buf.readableBytes()];
		buf.readBytes(b);
		bout.write(b);
		
		if(readedSize>=fileInfo.getFileLength()){
			readedSize=0;
			bout.close();
			out.close();
			fileInfo=null;
		}
		
		ctx.writeAndFlush(Unpooled.copiedBuffer(ResultCode.success.getBytes()));
	}
}