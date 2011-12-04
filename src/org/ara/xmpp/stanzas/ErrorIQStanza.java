package org.ara.xmpp.stanzas;

public class ErrorIQStanza extends IQStanza
{
	public ErrorIQStanza(String id)
	{
		super(IQType.ERROR, id);
	}
	
	@Override
	public Stanza addChild(Stanza stanza)
	{
		if(childs.size() == 1)
			return null;
		else
			return super.addChild(stanza);
	}
}
