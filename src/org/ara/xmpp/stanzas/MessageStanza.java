package org.ara.xmpp.stanzas;

public abstract class MessageStanza extends Stanza
{

	public MessageStanza(String name) {
		super(name);
	}
	
	public MessageStanza(String name, boolean simple, boolean allowChilds) {
		super(name, simple, allowChilds);
	}

}
