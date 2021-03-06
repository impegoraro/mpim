package org.ara.legacy.msn;

import java.util.ArrayList;
import java.util.List;

import net.sf.jml.DisplayPictureListener;
import net.sf.jml.Email;
import net.sf.jml.MsnContact;
import net.sf.jml.MsnGroup;
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

import org.apache.commons.lang.StringEscapeUtils;
import org.ara.legacy.LegacyContact;
import org.ara.legacy.LegacyNetwork;
import org.ara.legacy.LegacyRoom;
import org.ara.legacy.LegacyUserStatus;
import org.ara.legacy.LoginResult;

public class LegacyMsn extends LegacyNetwork {

	private MsnMessenger messenger;
	private boolean signaled;
	private boolean sendPresence;
	
	public LegacyMsn()
	{
		loginHandler = null;
		contactListHandler = null;
		signaled = false;
		sendPresence = false;
		contactListReady = false;
	}
	
	/* Description: This method should be asynchronous and should call the LoginCompleted callback */
	@Override
	public void login(String username, String password)
	{
		assert(username != null && password != null);
		
		messenger = MsnMessengerFactory.createMsnMessenger(username, password);
		messenger.setSupportedProtocol(new MsnProtocol[] {MsnProtocol.MSNP12});

		messenger.addMessengerListener(new MpimMsnMessenger());
		messenger.addContactListListener(new MpimMsnContactList(this));
		messenger.addMessageListener(new MpimMsnMessage());
		messenger.login();
	}

	@Override
	public void logout()
	{
		messenger.logout();
		if(loginHandler != null)
			loginHandler.logout();
	}
	
	@Override
	public List<LegacyContact> getContacts()
	{
		MsnContact[] msnContacts = messenger.getContactList().getContacts();
		List<LegacyContact> list = new ArrayList<LegacyContact>(msnContacts.length);

		for(MsnContact contact : msnContacts)
			list.add(convertMsnContact(contact));

		return list;
	}
	
	@Override
	public LegacyContact getContact(String id)
	{
		assert(id != null);
		LegacyContact contact = null;
		
		MsnContact msnContact = messenger.getContactList().getContactByEmail(Email.parseStr(id));
		if(msnContact != null)
			contact = convertMsnContact(msnContact);

		if(contact.avatar == null) {
			MsnObject obj = msnContact.getAvatar();
			if(obj != null) {
				if(obj.getMsnObj() != null) {
					contact.avatar = obj.getMsnObj();
					contact.avatarSha1 = obj.getSha1d();
				} else {
					DisplayImageRetriever dir = new DisplayImageRetriever(this);
					messenger.retrieveDisplayPicture(obj, dir);
					holdOn();
					
					if(dir.picture != null) {
						contact.avatar = dir.picture;
						contact.avatarSha1 = dir.sha1;
					} else
						System.err.println("(EE) [Network: MSN] display image download failed");
				}
			}
		}
			
		
		return contact;
	}
	
	@Override
	public void sendMessage(String to, String msg)
	{
		assert(to != null && msg != null);

		messenger.sendText(Email.parseStr(to), StringEscapeUtils.unescapeHtml(msg));
	}
	
	@Override
	public void changedStatus(String show, String status)
	{
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
	
	@Override
	public String getID()
	{
		return messenger.getOwner().getEmail().getEmailAddress();
	}
	
	@Override
	public String getNickname()
	{
		return messenger.getOwner().getDisplayName();
	}
	
	@Override
	public void setNickname(String nickname)
	{
		assert(nickname != null);
		
		messenger.getOwner().setDisplayName(nickname);
	}

	@Override
	public void setAvatar(String encondedImage)
	{
		assert (encondedImage != null);
		String email;
		
		if(encondedImage.trim().length()==0)
			return;

		email = messenger.getOwner().getEmail().getEmailAddress();
		
		MsnObject pic = MsnObject.getInstance(email, Base64.decode(encondedImage));
		messenger.getOwner().setDisplayPicture(pic);

	}

	@Override
	public String getSelfAvatar()
	{
		MsnOwner own = messenger.getOwner();
		MsnObject obj = own.getDisplayPicture();

		if(obj == null)
			throw new NullPointerException();

		return new String(Base64.encode(obj.getMsnObj()));
	}
	
	@Override
	public String getAvatar(String user)
	{
		String email = messenger.getOwner().getEmail().getEmailAddress();
		MsnContact contact;
		MsnObject obj;
		String base64Picture = null;
		
		if(email.equals(user))
			return null;
		
		if((contact = messenger.getContactList().getContactByEmail(Email.parseStr(email))) == null) 
			return null;

		obj = contact.getAvatar();
		
		if(obj != null) {
			if(obj.getMsnObj() != null)
				base64Picture = new String(Base64.encode(obj.getMsnObj()));
			else {
				DisplayImageRetriever dir = new DisplayImageRetriever(this);

				messenger.retrieveDisplayPicture(obj, dir);
				// wait until the transfer is done 
				holdOn();
				
				if(dir.picture != null) 
					base64Picture = new String(Base64.encode(dir.picture));
			}
		}
			
		return base64Picture;
	}

	@Override
	public List<LegacyRoom> getChatRooms()
	{
		MsnSwitchboard msnsws[] = messenger.getActiveSwitchboards();
		List<LegacyRoom> list = new ArrayList<LegacyRoom>();
		List<LegacyContact> contacts; 
		LegacyRoom room;
		int i = 0;
		for(MsnSwitchboard sw: msnsws) {
			contacts = new ArrayList<LegacyContact>();
			if(sw.getAllContacts().length > 1) { 
				// This is a group chat
				for(MsnContact c : sw.getAllContacts()) {
					contacts.add(convertMsnContact(c));
				}
				
				room = new LegacyRoom(sw.hashCode() + "", contacts);
				list.add(room);
			}
		}
		return list;
	}
	
	@Override
	public boolean isChatRoom(String name)
	{
		MsnSwitchboard msnsws[] = messenger.getActiveSwitchboards();
		int i = 0;
		
		for(MsnSwitchboard sw: msnsws) {
			if(sw.getAllContacts().length > 1) 
				if((sw.hashCode() + "").equals(name))
					return true;
		}
		return false;
	}
	
	@Override
	public void sendGroupMessage(String room, String msg)
	{
		MsnSwitchboard sw = getChatRoom(room);
		System.out.println("(DEBUG) [Network: MSN] sending group message");
		if(sw != null) {
			System.out.println("(DEBUG) [Network: MSN] using switchboard");
			sw.sendText(msg);
		} else 
			System.out.println("(DEBUG) [Network: MSN] room not available: " + room);
	}
	
	/*@Override
	public int getChatRoomCount()
	{
		int count = 0;
		MsnSwitchboard[] sw = messenger.getActiveSwitchboards();
		
		for(int k = 0; k<sw.length; k++)
			if(sw[k].getAllContacts().length > 1)
				count ++;

		return count;
	}*/
	
	private MsnSwitchboard getChatRoom(String name)
	{
		MsnSwitchboard msnsws[] = messenger.getActiveSwitchboards();
		int i = 0;
		
		for(MsnSwitchboard sw: msnsws) {
			if(sw.getAllContacts().length > 1) 
				if((sw.hashCode() + "").equals(name))
					return sw;
		}
		return null;
	}
	/* Synchronization methods */
	
	public synchronized void holdOn()
	{
		try {
			
			if(!signaled)
				this.wait();
		} catch (InterruptedException e) {
		}
		signaled = false;
	}

	public synchronized void wakeUp()
	{
		signaled = true;
		this.notify();
	}
	
	/* Private and Protected Methods */
	
	private LegacyContact convertMsnContact(MsnContact msnContact)
	{
		LegacyContact legContact = new LegacyContact();
		MsnObject obj;
		
		legContact.displayName = msnContact.getDisplayName();
		legContact.email = msnContact.getEmail().getEmailAddress();
		legContact.personalMessage = msnContact.getPersonalMessage();
		 
		if(msnContact.getStatus() == MsnUserStatus.ONLINE)
			legContact.status = LegacyUserStatus.AVAILABLE;
		else if(msnContact.getStatus() == MsnUserStatus.AWAY || msnContact.getStatus() == MsnUserStatus.BE_RIGHT_BACK || msnContact.getStatus() == MsnUserStatus.IDLE || msnContact.getStatus() == MsnUserStatus.OUT_TO_LUNCH)
			legContact.status = LegacyUserStatus.AWAY;
		else if(msnContact.getStatus() == MsnUserStatus.BUSY)
			legContact.status = LegacyUserStatus.BUSY; 
		//else if(msnContact.getStatus() == MsnUserStatus.OFFLINE && msnContact.getOldStatus() != MsnUserStatus.OFFLINE)
			//legContact.status = LegacyUserStatus.UNAVAILABLE;
		else
			legContact.status = LegacyUserStatus.UNAVAILABLE;
			
		for(MsnGroup grp : msnContact.getBelongGroups())
			legContact.addGroup(grp.getGroupName());
		
		if((obj = msnContact.getAvatar()) != null) {
			legContact.avatar = obj.getMsnObj();
			legContact.avatarSha1 = obj.getSha1d();
		}
		
		return legContact;
	}
	
	private class DisplayImageRetriever implements DisplayPictureListener
	{
		private LegacyMsn handle;
		public byte[] picture;
		public String sha1;

		public DisplayImageRetriever(LegacyMsn handle)
		{
			this.handle = handle;
			picture = null;
			sha1 = null;
		}

		@Override
		public void notifyMsnObjectRetrieval(MsnMessenger messenger, DisplayPictureRetrieveWorker worker, MsnObject msnObject, 
				ResultStatus result, byte[] resultBytes, Object context) {
			
			if(result == ResultStatus.GOOD) {
				picture = resultBytes;
				sha1 = msnObject.getSha1d();
			}
			
			handle.wakeUp();
		}
	}
	
	
	/* Definition of MSN Callback handlers */
	private class MpimMsnMessenger extends MsnMessengerAdapter
	{
		public void loginCompleted(MsnMessenger messenger)
		{
			System.out.println("(II) [Network: MSN] Log in as " + messenger.getOwner().getEmail());
			messenger.getOwner().setStatus(MsnUserStatus.ONLINE);
			
			if(loginHandler != null)
				loginHandler.loginCompleted(LoginResult.LOGIN_SUCCESSFUL);
			else 
				System.err.println("(EE) [Network: MSN] login callback handlers not installed");
		}

		public void logout(MsnMessenger messenger)
		{
			System.out.println("(II) [Network: MSN] Log out from " + messenger.getOwner().getEmail());
			
			// TODO: What to do when logout happens
			if(loginHandler != null)
				loginHandler.loginFailed();
			else 
				System.err.println("(EE) [Network: MSN] login callback handlers not installed");
		}

		public void exceptionCaught(MsnMessenger messenger, Throwable throwable)
		{
			System.err.println("(EE) [Network: MSN] Caught exception: " + throwable);
			System.err.println("------------------------------------------------------");
			throwable.printStackTrace();
			System.err.println("------------------------------------------------------");
			
			if(loginHandler != null)
				loginHandler.loginCompleted(LoginResult.LOGIN_FAILED);
			else 
				System.err.println("(EE) [Network: MSN] login callback handlers not installed");
		}
	}

	private class MpimMsnContactList extends MsnContactListAdapter
	{
		LegacyMsn msn;
		
		public MpimMsnContactList(LegacyMsn msn)
		{
			this.msn = msn;
		}
		
		public void contactListInitCompleted(MsnMessenger messenger)
		{
			msn.contactListReady = true;
			msn.sendPresence = true;
			if(contactListHandler != null) {
				contactListHandler.contactListReady();
			}
			else 
				System.err.println("(EE) [Network: MSN] contact list callback handlers not installed");
		}

		public void contactStatusChanged(MsnMessenger messenger, MsnContact contact)
		{
			// not ready to send presence stanzas
			if(!msn.sendPresence) 
				return;

			if(contactListHandler != null) {
				LegacyContact legContact = convertMsnContact(contact);
				contactListHandler.contactChangedStatus(legContact);
			} else 
				System.err.println("(EE) [Network: MSN] contact list callback handlers not installed");

		}
	}

	private class MpimMsnMessage extends MsnMessageAdapter
	{
		public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact msnContact)
		{
			if(messagesHandler != null) {
				System.out.println("(II) [Network: MSN] instant message received");
				
				if(switchboard.getAllContacts().length > 1) {
					String to = messenger.getOwner().getDisplayName();
					String from = msnContact.getDisplayName();
					
					System.out.println("(II) [Network: MSN] group message");
					messagesHandler.receivedGroupChatMessage(getRoomFrom(switchboard), from, to, from, message.getContent());
				} else {
					String to = messenger.getOwner().getEmail().getEmailAddress();
					String from = msnContact.getEmail().getEmailAddress();
					
					System.out.println("(II) [Network: MSN] normal message");
					messagesHandler.receivedMessage(from, to, message.getContent());
				}
			} else
				System.err.println("(EE) [Network: MSN] message callback handlers not installed");
		}

		public void controlMessageReceived(MsnSwitchboard switchboard, MsnControlMessage message, MsnContact contact)
		{
			
		}

		public void datacastMessageReceived(MsnSwitchboard switchboard, MsnDatacastMessage message, MsnContact contact)
		{

		}
	}
	
	/*private class MpimMsnSwitchboard implements MsnSwitchboardListener
	{
		@Override
		public void contactJoinSwitchboard(MsnSwitchboard arg0, MsnContact arg1)
		{
			System.out.println("(DEBUG) [Network: MSN] contact " + arg1.getDisplayName() + ", joined the switchboard");
		}

		@Override
		public void contactLeaveSwitchboard(MsnSwitchboard arg0, MsnContact arg1)
		{
			System.out.println("(DEBUG) [Network: MSN] contact " + arg1.getDisplayName() + ", leaved the switchboard");
		}

		@Override
		public void switchboardClosed(MsnSwitchboard arg0)
		{
			System.out.println("(DEBUG) [Network: MSN] the switchboard has been closed");
		}

		@Override
		public void switchboardStarted(MsnSwitchboard arg0)
		{
			System.out.println("(DEBUG) [Network: MSN] the switchboard has been started");
		}
		
	}*/
	
	private String getRoomFrom(MsnSwitchboard sw)
	{
		MsnSwitchboard msnsws[] = messenger.getActiveSwitchboards();
		int i = 0;
		
		for(MsnSwitchboard tmp: msnsws) {
			if(tmp.getAllContacts().length > 1) 
				if(tmp.hashCode() == sw.hashCode())
					return sw.hashCode() + "";
			i++;
		}
		
		return null;
	}
}
