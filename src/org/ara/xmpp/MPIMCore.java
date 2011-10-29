package org.ara.xmpp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class MPIMCore
{
	private volatile Selector selector;
	private ServerSocketChannel sChan;
	//private List<SocketChannel> sockets;
		
	ConnectionState state;
	
	public MPIMCore()
	{
		//super("MPIM_Core");
		try {
			selector = Selector.open();
			sChan = ServerSocketChannel.open();
			InetAddress addr = InetAddress.getByName("0.0.0.0");
			InetSocketAddress iaddr = new InetSocketAddress(addr, 5222);
			sChan.configureBlocking(false);
			sChan.socket().bind(iaddr);
			sChan.register(selector, SelectionKey.OP_ACCEPT);
			//sockets = new LinkedList<SocketChannel>();
		} catch (IOException e) {
		}

		//this.start();
	}

	public void run()
	{
		Iterator<SelectionKey> it;
		
		try {
			while(true) {
				selector.select();

				it = selector.selectedKeys().iterator();
				while(it.hasNext()) {
					SelectionKey key = (SelectionKey) it.next();

					it.remove();
					if(key == null) {
						System.out.println("Got a null key");
						System.exit(1);
					}
					if(!key.isValid()) {
						continue;
					}

					//Finish connection in case of an error 
					if(key.isConnectable()) {
						SocketChannel ssc = (SocketChannel) key.channel();
						if(ssc.isConnectionPending())
							ssc.finishConnect();
					}

					// Incoming connection, proceed with procedure to add the peer's socket to the list
					if(key.isAcceptable()) { 
						ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
						SocketChannel newClient = ssc.accept();
						System.out.println("(II) Accepted incomming connection form " + ssc.socket().getInetAddress().getCanonicalHostName());
						
						MpimAuthenticate auth = new MpimAuthenticate(selector,  newClient);
						auth.run();
					}

					if(key.isReadable()) {
						/*SocketChannel sc = (SocketChannel) key.channel();
						ByteBuffer data = ByteBuffer.allocate(sc.socket().getSendBufferSize());

						if(sc.read(data) == -1)
							continue; // bad packet

						String x = new String(data.array());*/

						//sc.configureBlocking(true);
						
						MpimParseInput mpi = new MpimParseInput(key);
						mpi.run();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String args[])
	{
		MPIMCore mpim = new MPIMCore();
		
		System.out.println("(II) Mpim server has been initialized");
		System.out.println("(II) Waiting for connections...");
		mpim.run();
	}
}