package org.ara.legacy;

import java.util.List;

public class LegacyRoom
{
	private String roomname;
	private List<LegacyContact> contacts;
	private int hash;
	
	public LegacyRoom(String name)
	{
		this(name, null);
		this.hash = 0;
	}
	
	public LegacyRoom(String name, List<LegacyContact> contacts)
	{
		this.roomname = name;
		this.contacts = contacts;
		this.hash = 0;
	}
	
	public void setRoomName(String name)
	{
		roomname = name;
	}
	
	public String getRoomName()
	{
		return roomname;
	}
	
	public List<LegacyContact>getContacts()
	{
		return contacts;
	}
	
	public void setHash(int hash)
	{
		this.hash = hash;
	}
	
	public int getHash()
	{
		return this.hash;
	}
}
