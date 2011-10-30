package org.ara.xmpp.stanzas;

public class PresenceStanza extends Stanza
{

	public PresenceStanza(String from, String to, String status, String show)
	{
		super("presence");
		
		assert(from != null && to != null && status != null && show != null);
		Stanza sshow;
		Stanza sstatus;
		
		addAttribute("from", from);
		addAttribute("to", to);
		
		sshow = new Stanza("show", false, false);
		sshow.setText(show);
		
		sstatus= new Stanza("status", false, false);
		sstatus.setText(status);
		
		addChild(sshow);
		addChild(sstatus);
	}
	
	public PresenceStanza(String from, String to)
	{
		super("presence");
		
		assert(from != null && to != null);
		
		addAttribute("from", from);
		addAttribute("to", to);
		
	}
	
	public void setStatus(String status)
	{
		assert(status != null);
		Stanza sstatus;
		
		sstatus = new Stanza("status", false, false);
		sstatus.setText(status);
		
		addChild(sstatus);
	}
	
	public void setShow(String show)
	{
		assert(show != null);
		Stanza sshow;
		
		sshow = new Stanza("show", false, false);
		sshow.setText(show);
		
		addChild(sshow);
	}
}
