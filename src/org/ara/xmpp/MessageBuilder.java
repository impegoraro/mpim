package org.ara.xmpp;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.ara.legacy.LegacyContact;
import org.ara.legacy.LegacyUserStatus;
import org.ara.xmpp.stanzas.IQStanza;
import org.ara.xmpp.stanzas.IQStanza.IQType;
import org.ara.xmpp.stanzas.PresenceStanza;
import org.ara.xmpp.stanzas.Stanza;

public class MessageBuilder
{
	static int roster_ver = 0;
	
	public static void sendRoster(XMPPConnection out, String id, String email, List<LegacyContact> contacts)
	{
		Stanza roster = new IQStanza(IQType.RESULT, id);
		Stanza query = new Stanza("query");

		query.addAttribute("xmlns", "jabber:iq:roster");
		
		roster.addAttribute("to", email);
		roster.addAttribute("ver", "ver"+ roster_ver++);		
		roster.addChild(query);		
		
		try {
			for(LegacyContact cont : contacts) {
				Stanza item = new Stanza("item");

				item.addAttribute("jid", cont.email);
				item.addAttribute("name", StringEscapeUtils.escapeHtml(cont.displayName));
				item.addAttribute("subscription", "both");

				if(cont.groups != null) {
					for(String grp : cont.groups) {
						Stanza group = new Stanza("group", false, false);
						group.setText(grp);
						item.addChild(group);
					}
				}
				query.addChild(item);
			}
			

			out.write(roster);
			

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static PresenceStanza bluildContactPresenceStanza(LegacyContact contact){
		//PresenceStanza presence = new PresenceStanza(contact.getEmail().getEmailAddress(), email);
		PresenceStanza presence = new PresenceStanza(contact.email, null);
		
		if(contact.status == LegacyUserStatus.AVAILABLE)
			presence.setShow("available");
		else if(contact.status == LegacyUserStatus.AWAY)
			presence.setShow("away");
		else if(contact.status == LegacyUserStatus.BUSY)
			presence.setShow("dnd");
		//else if(contact.status == LegacyUserStatus.UNAVAILABLE)
			//presence.addAttribute("type", "unavailable");
		else 
			presence.addAttribute("type", "unavailable");
			
		if(contact.personalMessage != null && contact.personalMessage.length() > 0 )
			presence.setStatus(contact.personalMessage);

		if(contact.avatar != null)
			presence.setAvatar(contact.avatarSha1);
		else 
			presence.setNoAvatar();
		
		return presence;
	}
}
