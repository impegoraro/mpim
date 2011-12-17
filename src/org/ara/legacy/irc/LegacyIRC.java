package org.ara.legacy.irc;

import java.util.ArrayList;
import java.util.List;

import jerklib.Channel;
import jerklib.ConnectionManager;
import jerklib.Profile;
import jerklib.Session;
import jerklib.events.ChannelMsgEvent;
import jerklib.events.IRCEvent;
import jerklib.events.IRCEvent.Type;
import jerklib.events.JoinCompleteEvent;
import jerklib.events.listeners.IRCEventListener;

import org.apache.commons.lang.StringEscapeUtils;
import org.ara.legacy.LegacyContact;
import org.ara.legacy.LegacyNetwork;
import org.ara.legacy.LegacyUserStatus;
import org.ara.legacy.LoginResult;

public class LegacyIRC extends LegacyNetwork implements IRCEventListener
{
	private ConnectionManager manager;
	private String self;
	private String hostname = "86.65.39.15";
	private Session session;
	private String channel = "#HarexTeam";
	private Channel connectedChannel;
	
	public LegacyIRC(String nick, String host)
	{
		assert(nick != null && host != null);
		self = nick;
		hostname = host;
		connectedChannel = null;
		
		manager = new ConnectionManager(new Profile() {
			@Override
			public String getThirdNick() {
				// TODO Auto-generated method stub
				return self + "123";
			}

			@Override
			public String getSecondNick() {
				// TODO Auto-generated method stub
				return self + "322";
			}

			@Override
			public String getName() {
				// TODO Auto-generated method stub
				return self;
			}

			@Override
			public String getFirstNick() {
				// TODO Auto-generated method stub
				return self + "4321";
			}

			@Override
			public String getActualNick() {
				// TODO Auto-generated method stub
				return self;
			}
		});
	}
	
	@Override
	public void login(String username, String password)
	{
		System.out.println("(II) [Network: IRC] Connecting to '"+ hostname + "'");
		session = manager.requestConnection(hostname);
		session.addIRCEventListener(this);
	}

	@Override
	public void logout()
	{
		session.close("I'm out of here");
	}

	@Override
	public List<LegacyContact> getContacts()
	{
		List<LegacyContact> list = new ArrayList<LegacyContact>(connectedChannel.getNicks().size());
		
		if(connectedChannel.getNicks() != null) 
			for(String str : connectedChannel.getNicks())
				list.add(convertIRCContact(str));
		
		return list;
	}

	@Override
	public LegacyContact getContact(String id)
	{
		String tmp;
		if(connectedChannel.getNicks() != null) 
			for(String str : connectedChannel.getNicks()) {
				tmp = str + "@" + hostname; 
				if(tmp.equals(id))
					return(convertIRCContact(str));
			}
		return null;
	}

	@Override
	public void changedStatus(String show, String status)
	{

	}

	@Override
	public void sendMessage(String to, String ms)
	{
		if(connectedChannel != null)
			connectedChannel.say(ms);
			
	}

	@Override
	public String getID()
	{
		return self  + "@" + hostname;
	}

	@Override
	public String getNickname()
	{
		return self;
	}

	@Override
	public void setNickname(String nickname)
	{
		
	}

	@Override
	public void setAvatar(String encondedImage)
	{

	}

	@Override
	public String getSelfAvatar()
	{
		return null;
	}

	@Override
	public String getAvatar(String user)
	{
		return null;
	}

	// Method from IRCEventListener
	@Override
	public void recieveEvent(IRCEvent event) {
		if (event.getType() == Type.CONNECT_COMPLETE) {
			System.out.println("(II) [Network: IRC] Connection completed and joining channel: " + channel);
			event.getSession().joinChannel(channel);

			if(loginHandler != null)
				loginHandler.loginCompleted(LoginResult.LOGIN_SUCCESSFUL);
			
		} else if (event.getType() == Type.CHANNEL_MESSAGE) {
			ChannelMsgEvent me = (ChannelMsgEvent) event;

			System.out.println("(II) " + me.getNick() + " says: " + me.getMessage());
			if(messagesHandler != null)
				messagesHandler.receivedMessage(me.getNick(), self, StringEscapeUtils.escapeHtml(me.getMessage()));
			
		} else if (event.getType() == Type.JOIN_COMPLETE) {
			JoinCompleteEvent jce = (JoinCompleteEvent) event;
			System.out.println("(II) [Network: IRC] Successfully joined to channel: " + channel);
			connectedChannel = jce.getChannel();
			contactListReady = true;
		}
	}

	
	private LegacyContact convertIRCContact(String nick)
	{
		LegacyContact legContact = new LegacyContact();
		
		legContact.displayName = nick;
		legContact.email = nick + "@" + hostname;
		legContact.personalMessage = null;
		 
		legContact.status = LegacyUserStatus.AVAILABLE;
			
		legContact.avatar = null;
		legContact.avatarSha1 = null;
		
		return legContact;
	}
}
