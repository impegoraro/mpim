package org.ara.xmpp;

import java.io.IOException;

import net.sf.jml.MsnContact;
import net.sf.jml.MsnGroup;
import net.sf.jml.MsnObject;
import net.sf.jml.MsnUserStatus;

import org.ara.MPIMMessenger;
import org.ara.xmpp.stanzas.IQStanza;
import org.ara.xmpp.stanzas.IQStanza.IQType;
import org.ara.xmpp.stanzas.PresenceStanza;
import org.ara.xmpp.stanzas.Stanza;

public class MessageBuilder
{
	static int roster_ver = 0;
	
	public static void sendRoster(XMPPConnection out, String id, String email, MsnContact[] contacts)
	{
		Stanza roster = new IQStanza(IQType.RESULT, id);
		Stanza query = new Stanza("query");

		query.addAttribute("xmlns", "jabber:iq:roster");
		
		roster.addAttribute("to", email);
		roster.addAttribute("ver", "ver"+ roster_ver++);		
		roster.addChild(query);		
		
		try {
			for(MsnContact cont : contacts) {
				Stanza item = new Stanza("item");

				item.addAttribute("jid", cont.getEmail().toString());
				item.addAttribute("name", MPIMMessenger.encodeHTML(cont.getDisplayName()));
				item.addAttribute("subscription", "both");

				if(cont.getBelongGroups() != null && cont.getBelongGroups().length > 0) {
					for(MsnGroup grp : cont.getBelongGroups()) {
						Stanza group = new Stanza("group", false, false);
						group.setText(grp.getGroupName());
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
	
	public static PresenceStanza bluildContactPresenceStanza(String email, MsnContact contact){
		//PresenceStanza presence = new PresenceStanza(contact.getEmail().getEmailAddress(), email);
		PresenceStanza presence = new PresenceStanza(contact.getEmail().getEmailAddress(), null);
		MsnObject obj;
		
		if(contact.getStatus() == MsnUserStatus.AWAY || contact.getStatus() == MsnUserStatus.BE_RIGHT_BACK || contact.getStatus() == MsnUserStatus.IDLE || contact.getStatus() == MsnUserStatus.OUT_TO_LUNCH)
			presence.setShow("away");
		
		if(contact.getStatus() == MsnUserStatus.BUSY)
			presence.setShow("dnd");
		
		if(contact.getStatus() == MsnUserStatus.OFFLINE && contact.getOldStatus() != MsnUserStatus.OFFLINE)
			presence.addAttribute("type", "unavailable");

		if(contact.getPersonalMessage() != null && contact.getPersonalMessage().length() > 0 )
			presence.setStatus(contact.getPersonalMessage());

		if((obj = contact.getAvatar()) != null)
			presence.setAvatar(obj.getSha1d());
		else 
			presence.setNoAvatar();
		
		return presence;
	}
}
