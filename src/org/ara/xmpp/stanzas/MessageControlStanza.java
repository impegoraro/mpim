package org.ara.xmpp.stanzas;

public class MessageControlStanza extends MessageStanza
{

	public MessageControlStanza(String from, String to) 
	{
		super("message");
		
		assert (to != null && from != null);
		Stanza composing = new Stanza("composing", true, false);
		
		composing.addAttribute("xmlns", "http://jabber.org/protocol/chatstates");
		
		addAttribute("to", to);
		addAttribute("from", from);
		addAttribute("type", "chat");
		
		addChild(composing);
	}

}
