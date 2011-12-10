package org.ara.xmpp;

import org.ara.legacy.LegacyNetwork;

public class Proxy 
{
	private XMPPConnection connection;
	private LegacyNetwork legacyHandle;
	
	public Proxy(XMPPConnection con, LegacyNetwork handle)
	{
		this.connection = con;
		this.legacyHandle = handle;
	}
	
	public XMPPConnection getConnection()
	{
		return connection;
	}
	
	public LegacyNetwork getHandle()
	{
		return legacyHandle;
	}
	
	public synchronized void close()
	{
		if(connection != null && legacyHandle != null) {
			legacyHandle.logout();
			/* Since the introduction of LegacyNetwork Callbacks we close the connection
			 * to the xmpp client whenever the legacy network connection goes down */
			//connection.close();
		}
	}
}
