package org.ara;

import java.io.IOException;

import net.sf.jml.Email;
import net.sf.jml.MsnContact;
import net.sf.jml.MsnList;
import net.sf.jml.MsnMessenger;
import net.sf.jml.MsnOwner;
import net.sf.jml.MsnProtocol;
import net.sf.jml.MsnSwitchboard;
import net.sf.jml.MsnUserStatus;
import net.sf.jml.event.MsnContactListAdapter;
import net.sf.jml.event.MsnMessageAdapter;
import net.sf.jml.event.MsnMessengerAdapter;
import net.sf.jml.impl.MsnMessengerFactory;
import net.sf.jml.message.MsnControlMessage;
import net.sf.jml.message.MsnDatacastMessage;
import net.sf.jml.message.MsnInstantMessage;

import org.ara.xmpp.ConnectionState;
import org.ara.xmpp.MessageBuilder;
import org.ara.xmpp.MpimAuthenticate;
import org.ara.xmpp.XMPPConnection;
import org.ara.xmpp.stanzas.MessageChatStanza;
import org.ara.xmpp.stanzas.MessageControlStanza;

public class MPIMMessenger extends Thread
{
	private String email;
	private String password;
	private MsnMessenger messenger;
	private MpimAuthenticate mpimAuth;
	private XMPPConnection connection;
	private boolean sendPresence;
	private boolean signaled;
	
	public MPIMMessenger(String stremail, String strpwd, MpimAuthenticate ma, XMPPConnection con)
	{
		assert(stremail != null && strpwd != null && ma != null && con != null);
		email = stremail;
		password = strpwd;
		messenger = MsnMessengerFactory.createMsnMessenger(email, password);
		messenger.setSupportedProtocol(new MsnProtocol[] {MsnProtocol.MSNP12});
		mpimAuth = ma;
		connection = con;
		signaled = sendPresence = false;
		
		initListeners(messenger);
	}
	
	public synchronized void setAllowPresence(boolean allow)
	{
		sendPresence = allow;
	}
	
	public void sendMessage(String to, String msg)
	{
		
		messenger.sendText(Email.parseStr(to), msg);
	}

	public void sendTyping(String to)
	{
		MsnSwitchboard tmp[] = messenger.getActiveSwitchboards();
		
		for(int i = 0; i< tmp.length; i++) {
			MsnControlMessage mc = new MsnControlMessage();
			mc.setTypingUser(to);
			tmp[i].sendMessage(mc);
		}
	}
	
	public void sendRoster(String id) {
		this.goToSleep();
		
		MessageBuilder.sendRoster(connection, id, email, getContacts());

	}
	
	public void sendContactListPresence(){
		// not ready to send the presence
		if(!sendPresence)
			return;
		
		for( MsnContact cont : messenger.getContactList().getContactsInList(MsnList.AL)){
			if(cont.getStatus() == MsnUserStatus.OFFLINE)
				continue;
			
			try {
				connection.write(MessageBuilder.bluildContactPresenceStanza(email, cont));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void setStatus(String show, String status){
		MsnOwner own = messenger.getOwner();
		
		if(show == null || show.equals("chat"))
			own.setStatus(MsnUserStatus.ONLINE);
		else if(show.equals("away") || show.equals("xa"))
			own.setStatus(MsnUserStatus.AWAY);
		else if(show.equals("dnd"))
			own.setStatus(MsnUserStatus.BUSY);
		
		if(status == null)
			status = "";
		own.setPersonalMessage(status);
	}
	
	public MsnContact[] getContacts(){
		
		MsnContact[] c;
		
		while(true) {
			c = messenger.getContactList().getContactsInList(MsnList.AL);
			
			if(c == null || c.length == 0) {
				goToSleep();
			} else {
				break;
			}
		}
		
		return messenger.getContactList().getContactsInList(MsnList.AL);
	}

	public void start()
	{
		messenger.login();
	}

	private void initListeners(MsnMessenger messenger)
	{
		messenger.addMessengerListener(new MPIM_MsnMessengerAdapter());
		messenger.addContactListListener(new MPIM_MsnContactListAdapter());
		messenger.addMessageListener(new MPIM_MsnMessageAdapter());
	}
	
	private class MPIM_MsnMessengerAdapter extends MsnMessengerAdapter
	{
		public void loginCompleted(MsnMessenger messenger)
		{
			System.out.println(messenger.getOwner().getEmail() + " login");
			messenger.getOwner().setStatus(MsnUserStatus.ONLINE);
			mpimAuth.setState(ConnectionState.AUTHENTICATED);
			mpimAuth.wakeMePlease();
		}

		public void logout(MsnMessenger messenger)
		{
			System.out.println(messenger.getOwner().getEmail() + " logout");
			mpimAuth.setState(ConnectionState.NONAUTHENTICATED);
			mpimAuth.wakeMePlease();
		}

		public void exceptionCaught(MsnMessenger messenger, Throwable throwable)
		{
			System.out.println("caught exception: " + throwable);
			throwable.printStackTrace();
			mpimAuth.setState(ConnectionState.DISCONNECTED);
			mpimAuth.wakeMePlease();
		}
	}
	
	class MPIM_MsnContactListAdapter extends MsnContactListAdapter
	{
		public void contactListInitCompleted(MsnMessenger messenger)
		{
			wakeMePlease();
		}

		public void contactStatusChanged(MsnMessenger messenger, MsnContact contact)
		{
			// not ready to send presence stanzas
			if(!sendPresence) 
				return;
			
			System.out.println(contact.getDisplayName() + ":" + contact.getStatus() + " from " + contact.getOldStatus());
			System.out.println("(II) Sending presence stanza");

			try {
				connection.write(MessageBuilder.bluildContactPresenceStanza(email, contact));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public class MPIM_MsnMessageAdapter extends MsnMessageAdapter
	{
		public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact contact)
		{
			try {
				connection.write(new MessageChatStanza(contact.getEmail().getEmailAddress(),email, encodeHTML(message.getContent())));
			} catch (IOException e) {
			}
					
		}

		public void controlMessageReceived(MsnSwitchboard switchboard, MsnControlMessage message, MsnContact contact)
		{
			String tmp;
			if((tmp = message.getTypingUser()) != null) {
				try {
					connection.write(new MessageControlStanza(tmp, email));
				} catch (IOException e) {
				}
			}
		}

		public void datacastMessageReceived(MsnSwitchboard switchboard, MsnDatacastMessage message, MsnContact contact)
		{
			//such as Nudge
			switchboard.sendMessage(message);
			System.out.println(message);
		}
	}
	
	public synchronized void close()
	{
		messenger.logout();
	}
	
	public synchronized void goToSleep()
	{
		try {
			if(!signaled)
				this.wait();
		} catch (InterruptedException e) {
		}
		signaled = true;
	}
	
	public synchronized void wakeMePlease()
	{
		signaled = true;
		this.notify();
	}
	
	public static String encodeHTML(String s) {
		StringBuffer out = new StringBuffer();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&' || c == '\'') {
				out.append("&#" + (int) c + ";");
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
