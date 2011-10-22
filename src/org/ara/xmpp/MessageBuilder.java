package org.ara.xmpp;

import java.io.StringWriter;

import net.sf.jml.MsnContact;
import net.sf.jml.MsnGroup;
import net.sf.jml.MsnUserStatus;

public class MessageBuilder
{
	public static String buildRoster(String id, String email, MsnContact[] contacts)
	{
		StringWriter sw = new StringWriter();

		sw.append("<iq id='" + id + "' to='" + email + "' type='result'>\n");

		sw.append("  <query xmlns='jabber:iq:roster'>\n");

		for(MsnContact cont : contacts) {
			sw.append("    <item jid='" + cont.getEmail().toString() + "' name='" + cont.getDisplayName()
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
	
	public static String bluildContactPresence(String email, MsnContact contact){
		boolean added = true;
		String toSend = "<presence from=\""+contact.getEmail().getEmailAddress()+"\" to=\""+email+"\" ";
		
		if(contact.getStatus() == MsnUserStatus.AWAY || contact.getStatus() == MsnUserStatus.BE_RIGHT_BACK || contact.getStatus() == MsnUserStatus.IDLE || contact.getStatus() == MsnUserStatus.OUT_TO_LUNCH){
			toSend += "> <show>away</show>";
			added = true;
		}
		if(contact.getStatus() == MsnUserStatus.BUSY || contact.getStatus() == MsnUserStatus.OUT_TO_LUNCH){
			toSend += "> <show>dnd</show>";
		}
		
		
		return toSend;
	}
}