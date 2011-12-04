package org.ara;

import java.io.IOException;

import net.sf.jml.DisplayPictureListener;
import net.sf.jml.Email;
import net.sf.jml.MsnContact;
import net.sf.jml.MsnList;
import net.sf.jml.MsnMessenger;
import net.sf.jml.MsnObject;
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
import net.sf.jml.message.p2p.DisplayPictureRetrieveWorker;
import net.sf.jml.util.Base64;

import org.ara.xmpp.ConnectionState;
import org.ara.xmpp.MessageBuilder;
import org.ara.xmpp.MpimAuthenticate;
import org.ara.xmpp.XMPPConnection;
import org.ara.xmpp.stanzas.MessageChatStanza;
import org.ara.xmpp.stanzas.MessageControlStanza;
import org.ara.xmpp.stanzas.VCard;

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

	public String getAccountName()
	{
		return messenger.getOwner().getDisplayName();
	}

	public void setAccountName(String name)
	{
		assert(name != null);

		messenger.getOwner().setDisplayName(name);
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

	public VCard createVCard(String email)
	{
		VCard vcard = null;
		String name;
		MsnObject obj;
		
		if(email == null)
			return null;
		
		if(email.equals(this.email)) {
			name = messenger.getOwner().getDisplayName();
			obj = messenger.getOwner().getDisplayPicture();
		} else {
			MsnContact contact = messenger.getContactList().getContactByEmail(Email.parseStr(email));

			name = contact.getDisplayName();
			obj = contact.getAvatar();
		}

		vcard = new VCard(encodeHTML(name));
		vcard.setEmail(email);

		if(obj != null) {
			if(obj.getMsnObj() != null)
				vcard.setAvatar(new String(Base64.encode(obj.getMsnObj())), "image/jpeg");
			else {
				DisplayImageRetriever dir = new DisplayImageRetriever(this);

				messenger.retrieveDisplayPicture(obj, dir);
				// wait until the transfer is done 
				goToSleep();
				System.out.println("(DEBUG) Woke up by the retriever");
				if(dir.picture != null) 
					vcard.setAvatar(new String(Base64.encode(dir.picture)), "image/jpeg");
			}}

		return vcard;
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

	public void setDisplayPicture(String base64Photo)
	{
		assert (base64Photo != null);

		if(base64Photo.trim().length()==0)
			return;

		//MsnObject pic = MsnObject.getInstance("me", Base64.decode(base64Photo));
		String tmpphoto = "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";
		MsnObject pic = MsnObject.getInstance(email, Base64.decode(tmpphoto));
		messenger.getOwner().setDisplayPicture(pic);
	}

	public String getDisplayPictureBase64()
	{
		MsnOwner own = messenger.getOwner();
		MsnObject obj = own.getDisplayPicture();

		if(obj == null)
			throw new NullPointerException();

		return new String(Base64.encode(obj.getMsnObj()));
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
			System.out.println("(II) Log in as " + messenger.getOwner().getEmail());
			messenger.getOwner().setStatus(MsnUserStatus.ONLINE);
			mpimAuth.setState(ConnectionState.AUTHENTICATED);
			mpimAuth.wakeMePlease();
		}

		public void logout(MsnMessenger messenger)
		{
			System.out.println("(II) Log out from " + messenger.getOwner().getEmail());
			mpimAuth.setState(ConnectionState.NONAUTHENTICATED);
			mpimAuth.wakeMePlease();
		}

		public void exceptionCaught(MsnMessenger messenger, Throwable throwable)
		{
			System.err.println("(EE) Caught exception: " + throwable);
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

			//System.out.println("(DEBUG) " + contact.getDisplayName() + ":" + contact.getStatus() + " from " + contact.getOldStatus());
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
		signaled = false;
	}

	public synchronized void wakeMePlease()
	{
		signaled = true;
		this.notify();
	}

	private class DisplayImageRetriever implements DisplayPictureListener
	{
		private MPIMMessenger handle;
		public MsnObject msnObject;
		public byte[] picture;

		public DisplayImageRetriever(MPIMMessenger handle)
		{
			this.handle = handle;
			picture = null;
		}

		@Override
		public void notifyMsnObjectRetrieval(MsnMessenger messenger, DisplayPictureRetrieveWorker worker, MsnObject msnObject, 
				ResultStatus result, byte[] resultBytes, Object context) {
			System.out.println("(DEBUG) Got a notification for avatar retrieval: Status is " + result);
			if(result == ResultStatus.GOOD) {
				picture = resultBytes;
			}
			
			System.out.println("(DEBUG) Waking up the mpim");
			handle.wakeMePlease();

		}

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
