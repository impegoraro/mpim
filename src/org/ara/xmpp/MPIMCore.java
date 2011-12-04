package org.ara.xmpp;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

public class MPIMCore
{
	private final static String prog_version = "0.5";
	private final static String prog_name = "mpim";
	private final static String[] prog_authors = { 
		"Ilan Pegoraro <impegoraro@ua.pt> ",
		"Renato Almeida <renato.almeida@ua.pt>"
	};
	private volatile Selector selector;
	private ServerSocketChannel sChan;

	ConnectionState state;

	public MPIMCore(int port)
	{
		try {
			InetSocketAddress iaddr = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port);

			selector = SelectorProvider.provider().openSelector();

			sChan = ServerSocketChannel.open();
			sChan.configureBlocking(false);
			sChan.socket().bind(iaddr);
			sChan.register(selector, SelectionKey.OP_ACCEPT);

		} catch (IOException e) {
			System.err.println("(EE) unable to set up server. Ending the application");
			System.exit(1);
		}
	}

	public static final String getVersion()
	{
		return prog_version;
	}

	public static final String getProgName()
	{
		return prog_name;
	}

	public static final String[] getAuthors()
	{
		return prog_authors;
	}

	public void run()
	{
		Iterator<SelectionKey> selectedKeys;

		try {
			while(true) {
				selector.select();

				selectedKeys = selector.selectedKeys().iterator();
				while(selectedKeys.hasNext()) {
					SelectionKey key = (SelectionKey) selectedKeys.next();
					selectedKeys.remove();

					if(!key.isValid()) {
						continue;
					}

					// Finish connection in case of an error 
					if(key.isConnectable()) {
						SocketChannel ssc = (SocketChannel) key.channel();

						if(ssc.isConnectionPending())
							ssc.finishConnect();

					} else if(key.isAcceptable()) { 
						// Incoming connection, proceed with procedure to add the peer's socket to the list

						ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
						SocketChannel newClient = ssc.accept();

						System.out.println("(II) Accepted incomming connection form " + newClient.socket().getInetAddress().getHostAddress());

						MpimAuthenticate auth = new MpimAuthenticate(selector,  newClient);
						auth.run();

					} else if(key.isReadable()) {
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
		MPIMCore mpim = new MPIMCore(5222);

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