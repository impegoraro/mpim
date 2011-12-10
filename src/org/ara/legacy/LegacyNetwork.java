package org.ara.legacy;

import java.util.List;

public abstract class LegacyNetwork
{
	protected boolean contactListReady;
	
	protected LoginCallbacks loginHandler;
	protected ContactListCallbacks contactListHandler;
	protected MessageCallbacks messagesHandler;

	abstract public void login(String username, String password);
	abstract public void logout();

	abstract public List<LegacyContact> getContacts();
	abstract public LegacyContact getContact(String id);
	
	abstract public void changedStatus(String show, String status);

	abstract public void sendMessage(String to, String ms);

	abstract public String getID();
	abstract public String getNickname();
	abstract public void setNickname(String nickname);
	abstract public void setAvatar(String encondedImage);
	abstract public String getSelfAvatar();
	abstract public String getAvatar(String user);

	public boolean contactListReady()
	{
		return contactListReady;
	}
	
	public void addCallbacks(LoginCallbacks handler)
	{
		assert(handler != null);

		loginHandler = handler;
	}

	public void addCallbacks(ContactListCallbacks handler)
	{
		assert(handler != null);

		contactListHandler = handler;
	}

	public void addCallbacks(MessageCallbacks handler)
	{
		assert(handler != null);

		messagesHandler = handler;
	}
	
}
