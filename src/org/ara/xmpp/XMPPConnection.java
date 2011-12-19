package org.ara.xmpp;

import java.io.IOException;
import java.net.Socket;
import java.util.Calendar;
import java.util.Random;

import org.ara.xmpp.stanzas.IQStanza;
import org.ara.xmpp.stanzas.IQStanza.IQType;
import org.ara.xmpp.stanzas.Stanza;

public class XMPPConnection
{
	private Entity entity;
	private Socket socket;
	private boolean useTLS;

	public XMPPConnection(Socket sock, String username, String pwd, String domain, String resource, boolean useTLS)
	{
		assert(sock != null && username != null && pwd != null && domain != null && resource != null);
		socket = sock;
		this.useTLS = useTLS;
		this.entity = new Entity(username, pwd, domain, resource);
	}
	
	public XMPPConnection(Socket sock)
	{
		this(sock, true);
	}
	
	public XMPPConnection(Socket sock, boolean useTLS)
	{
		assert(socket != null);
	
		entity = null;
		this.useTLS = useTLS;
		socket = sock;
	}

	public String getJID()
	{
		return entity.getJID();
	}

	public String getDomain()
	{
		return entity.getDomain();
	}
	
	public String getBareJID()
	{
		return entity.getEmail();
	}

	public synchronized void write(Stanza stanza) throws IOException
	{
		assert(stanza != null);

		socket.getOutputStream().write(stanza.getStanza().getBytes());
	}

	public synchronized Socket socket()
	{
		return socket;
	}
	
	public synchronized boolean isSecure()
	{
		return useTLS;
	}
	
	public synchronized void write(String str) throws IOException
	{
		assert(str != null);

		socket.getOutputStream().write(str.getBytes());
	}
	
	public synchronized void close()
	{
		try {
			System.out.println("(II) Clossing account " + entity.getEmail());
			Stanza stream = new Stanza("stream:stream");

			socket.getOutputStream().write(stream.endTag().getBytes());
			socket.close();

		} catch (IOException e) {
			System.err.println("(EE) exception when closing the socket");
			e.printStackTrace();
		}
	}

	public synchronized void sendPing()
	{
		Random r = new Random();
		IQStanza stanza;
		Stanza ping;

		r.setSeed(Calendar.getInstance().getTimeInMillis());
		stanza = new IQStanza(IQType.GET, "mpim" + r.nextInt());

		ping  = new Stanza("ping", true);
		ping.addAttribute("xmlns", "urn:xmpp:ping");
		stanza.addChild(ping);
		stanza.addAttribute("from", entity.getDomain());
		stanza.addAttribute("to", entity.getJID());

		try {
			write(stanza);
		} catch (IOException e) {

		}

	}
}
