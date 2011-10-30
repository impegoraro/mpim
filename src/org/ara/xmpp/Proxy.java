package org.ara.xmpp;

import org.ara.MPIMMessenger;

public class Proxy 
{
	private XMPPConnection connection;
	private MPIMMessenger msn;
	
	public Proxy(XMPPConnection con, MPIMMessenger msn)
	{
		this.connection = con;
		this.msn = msn;
	}
	
	public XMPPConnection getConnection()
	{
		return connection;
	}
	
	public MPIMMessenger getMSN()
	{
		return msn;
	}
	
	public synchronized void close()
	{
		if(connection != null && msn != null) {
			msn.close();
			connection.close();
		}
	}
}
