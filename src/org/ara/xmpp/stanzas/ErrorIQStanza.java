package org.ara.xmpp.stanzas;

public class ErrorIQStanza extends IQStanza
{
	public ErrorIQStanza(String id)
	{
		super(IQType.ERROR, id);
	}
	
	@Override
	public boolean addChild(Stanza stanza)
	{
		if(childs.size() == 1)
			return false;
		else
			return super.addChild(stanza);
	}
}
