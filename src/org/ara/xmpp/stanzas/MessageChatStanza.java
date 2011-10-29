package org.ara.xmpp.stanzas;

public class MessageChatStanza  extends Stanza
{
	public MessageChatStanza(String from, String to, String body)
	{
		super("message");
		
		assert (to != null && from != null && body != null);
		Stanza text = new Stanza("body", false, false);
		Stanza active = new Stanza("active", true, false);
		active.addAttribute("xmlns", "http://jabber.org/protocol/chatstates");
		
		addAttribute("to", to);
		addAttribute("from", from);
		addAttribute("type", "chat");
		
		text.setText(body);
		addChild(text);
		addChild(active);
	}
}
