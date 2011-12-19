package org.ara.legacy;

public interface MessageCallbacks
{
	public void receivedMessage(String from, String to, String msg);
	public void receivedGroupChatMessage(String room, String from, String to, String nick, String msg);
}
