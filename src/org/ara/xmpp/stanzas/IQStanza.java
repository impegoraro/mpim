package org.ara.xmpp.stanzas;

public class IQStanza extends Stanza
{
	public enum IQType
	{
		GET,
		SET,
		RESULT,
		ERROR
	}
	
	public IQStanza(IQType type, String id)
	{
		super("iq", false, true);
		
		assert(id != null);
		addAttribute("type", type.toString().toLowerCase());
		addAttribute("id", id);
		
	}
}
