package org.ara.xmpp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.http.ConnectionClosedException;


public class MpimCore extends Thread
{
	private final static String prog_version = "0.5";
	private final static String prog_name = "mpim";
	private final static String[] prog_authors = { 
		"Ilan Pegoraro <impegoraro@ua.pt> ",
		"Renato Almeida <renato.almeida@ua.pt>"
	};
	private final static int port = 5222; 

	// Class attributes 
	private Socket sock;

	public MpimCore(Socket client)
	{
		sock = client;
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

	@Override
	public void run()
	{
		MpimAuthenticate newUser = new MpimAuthenticate(sock); // Create a new authentication for this user, using default options
		System.out.println("(II) Authenticating new client '" + sock.getInetAddress().getHostAddress()+ "'");

		Proxy p = newUser.authenticate();

		if(newUser.getState() == ConnectionState.AUTHENTICATED) {
			System.out.println("(II) Client '" + sock.getInetAddress().getHostAddress()+ " (" + p.getConnection().getBareJID() + ")' has been authenticated");
			while(p.getConnection().socket().isConnected()) {
				MpimParseInput input = null;
				try {
					input = new MpimParseInput(p);
				} catch (ConnectionClosedException e) {
					break;
				}
				input.parse();
			}
		} else {
			System.err.println("(WW) The new user could not be authenticated");
		}
	}

	public static void main(String args[])
	{
		ServerSocket serverSock;
		Socket client;

		if(args.length > 0){
			if(args[0].equals("-v") || args[0].equals("--version")) {
				System.out.println(MpimCore.getProgName() + " v" + MpimCore.getVersion() + " A simple XMPP proxy to other proprietary instant messaging protocol.");
				for(String author : MpimCore.getAuthors()) {
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
		System.out.println("(II) "+ prog_name + " (v" + prog_version + ") server has been initialized");
		try {
			serverSock = new ServerSocket(port);
			while(true) {
				System.out.println("(II) Listening for connections on port " + port + "...");
				client = serverSock.accept();
				Thread mpim = new MpimCore(client);
				mpim.start();
				client = null;
			}	
		} catch (IOException e) {
			System.err.println("(EE) Unable to create and bind the socket to the port " + port);
		}
	}
}