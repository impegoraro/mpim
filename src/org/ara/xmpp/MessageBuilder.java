package org.ara.xmpp;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import net.sf.jml.MsnContact;
import net.sf.jml.MsnGroup;
import net.sf.jml.MsnUserStatus;

import org.ara.xmpp.stanzas.IQStanza;
import org.ara.xmpp.stanzas.IQStanza.IQType;
import org.ara.xmpp.stanzas.Stanza;

public class MessageBuilder
{
	static int roster_ver = 0;
	
	@Deprecated
	public static String buildRoster(String id, String email, MsnContact[] contacts)
	{
		StringWriter sw = new StringWriter();

		sw.append("<iq id='" + id + "' to='" + email + "' type='result' ver='ver15'>\n");

		sw.append("  <query xmlns='jabber:iq:roster'>\n");

		for(MsnContact cont : contacts) {
			sw.append("    <item jid='" + cont.getEmail().toString() + "' name='" +  cont.getEmail().toString() //cont.getDisplayName().replaceAll("(\"|')", "_")
					+ "' subscription='both'");
			if(cont.getBelongGroups() != null && cont.getBelongGroups().length > 0) {
				sw.append(">\n");
				for(MsnGroup grp : cont.getBelongGroups()) {
					sw.append("      <group>" + grp.getGroupName() + "</group>\n");
				}
				sw.append("    </item>\n");
			} else {
				sw.append("/>\n");
			}
		}
		sw.append("  </query>\n");
		sw.append("</iq>");

		return sw.toString();
	}

	public static void sendRoster(SocketChannel out, String id, String email, MsnContact[] contacts)
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
				item.addAttribute("name",cont.getEmail().toString()); //cont.getDisplayName().replaceAll("(\"|')", "_"));
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
			

			out.write(ByteBuffer.wrap(roster.getStanza().getBytes()));
			

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String bluildContactPresence(String email, MsnContact contact){
		boolean added = false;
		String toSend = "<presence from=\""+contact.getEmail().getEmailAddress()+"\" to=\""+email+"\"";

		if(contact.getStatus() == MsnUserStatus.AWAY || contact.getStatus() == MsnUserStatus.BE_RIGHT_BACK || contact.getStatus() == MsnUserStatus.IDLE || contact.getStatus() == MsnUserStatus.OUT_TO_LUNCH){
			toSend += "> \n<show>away</show>";
			added = true;
		}
		if(contact.getStatus() == MsnUserStatus.BUSY || contact.getStatus() == MsnUserStatus.OUT_TO_LUNCH){
			toSend += "> \n<show>dnd</show>";
			added = true;
		}
		if(contact.getStatus() == MsnUserStatus.OFFLINE && contact.getOldStatus() != MsnUserStatus.OFFLINE)
			toSend += "type=\"unavailable\"";


		if(contact.getPersonalMessage() != null && contact.getPersonalMessage().length() > 0 ){
			if(!added)
				toSend +=">";
			added = true;
			toSend += "\n<status>"+contact.getPersonalMessage()+"</status>";
		}
		if(!added)
			toSend += "/>";
		else
			toSend += "\n</presence>";

		return toSend;
	}
}
