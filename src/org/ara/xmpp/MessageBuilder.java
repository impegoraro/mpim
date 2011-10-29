package org.ara.xmpp;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

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
	
	public static boolean sendRoster(SocketChannel out, String id, String email, MsnContact[] contacts)
	{
		String response;
		
		response = "<iq id='" + id + "' to='" + email + "' type='result'>";
		response += "<query xmlns='jabber:iq:roster'>";

		try {
			out.write(ByteBuffer.wrap(response.getBytes()));
		
		for(MsnContact cont : contacts) {
			response = "<item jid='" + cont.getEmail().toString() + "' name='" + cont.getDisplayName()
					+ "' subscription='both'";
			if(cont.getBelongGroups() != null && cont.getBelongGroups().length > 0) {
				response +=">\n";
				for(MsnGroup grp : cont.getBelongGroups()) {
					response += "<group>" + grp.getGroupName() + "</group>";
				}
				response += "</item>";
			} else {
				response += "/>";
			}
			
			out.write(ByteBuffer.wrap(response.getBytes()));
		}
		response += "</query>";
		response += "</iq>";
		
		out.write(ByteBuffer.wrap(response.getBytes()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		return true;
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
