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
	private final static String prog_version = "0.1-alpha";
	private final static String prog_name = "mpim";
	private final static String[] prog_authors = { "Ilan Pegoraro <impegoraro@ua.pt> ", "Renato Almeida <renato.almeida@ua.pt>" };
	private volatile Selector selector;
	private ServerSocketChannel sChan;

	
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

	public final String getVersion()
	{
		return prog_version;
	}
	
	public final String getProgName()
	{
		return prog_name;
	}
	
	public final String[] getAuthors()
	{
		return prog_authors;
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

					// Finish connection in case of an error 
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
		
		if(args.length > 0){
			if(args[0].equals("-v") || args[0].equals("--version")) {
				System.out.println(mpim.getProgName() + " v" + mpim.getVersion() + " A simple XMPP proxy to other proprietary instant messaging protocol.");
				for(String author : mpim.getAuthors()) {
					System.out.println(author);
				}
				System.out.println("\nCopyright (C) 2011 Universidade de Aveiro\n" +
						"This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.\n" +
						"This is free software, and you are welcome to redistribute it"+ 
						"under certain conditions; type `show c' for details.");
				System.out.println("\n\n");
				System.exit(0);
			}
				
		}
		
		System.out.println("(II) Mpim server has been initialized");
		System.out.println("(II) Waiting for connections...");
		mpim.run();
	}
}