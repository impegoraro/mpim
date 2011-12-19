package org.ara.xmpp.stanzas;

public class MessageChatStanza  extends MessageStanza
{
	private Stanza body; 
	
	public MessageChatStanza(String from, String to, String body)
	{
		this(from, to);
		
		assert (body != null);
		this.body = new Stanza("body", false, false);
		
		
		this.body.setText(body);
		addChild(this.body);
	}
	
	public MessageChatStanza(String from, String to)
	{
		this(from, to, "chat", false);
	}
	
	public MessageChatStanza(String from, String to, String type, boolean tmp)
	{
		super("message");
		
		assert (to != null && from != null);
		Stanza active = new Stanza("active", true, false);
		active.addAttribute("xmlns", "http://jabber.org/protocol/chatstates");
		
		addAttribute("to", to);
		addAttribute("from", from);
		addAttribute("type", type);
		
		addChild(active);
		
		body = null;
	}
	
	public void setBody(String message)
	{
		if(body == null) {
			this.body = new Stanza("body", false, false);
			addChild(this.body);	
		}
		
		this.body.text = message == null ? "" : message;
	}
}
