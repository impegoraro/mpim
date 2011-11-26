package org.ara.xmpp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.ara.xmpp.stanzas.Stanza;

public class XMPPConnection 
{
	private Entity entity;
	private SocketChannel socket;
	private SelectionKey key;
	
	public XMPPConnection(SocketChannel sc, String username, String pwd, String domain, String resource)
	{
		assert(sc != null && username != null && pwd != null && domain != null && resource != null);
		
		this.entity = new Entity(username, pwd, domain, resource);
		this.socket = sc;
	}
	
	public String getJID()
	{
		return entity.getJID();
	}
	
	public String getBareJID()
	{
		return entity.getEmail();
	}
	
	public synchronized void write(Stanza stanza) throws IOException
	{
		assert(stanza != null);
		
		socket.write(ByteBuffer.wrap(stanza.getStanza().getBytes()));
		
	}
	
	public synchronized void close()
	{
		try {
			System.out.println("(II) Clossing account " + entity.getEmail());
			Stanza stream = new Stanza("stream:stream");
			
			socket.write(ByteBuffer.wrap(stream.endTag().getBytes()));
			socket.close();
			key.cancel();
			
		} catch (IOException e) {
			System.err.println("(EE) exception when closing the socket");
			e.printStackTrace();
		} catch(NullPointerException e) {
			
		}
	}
}
