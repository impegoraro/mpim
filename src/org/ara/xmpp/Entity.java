package org.ara.xmpp;

public class Entity {
	private String username;
	private String password;
	private String domain;
	private String resource;
	
	public Entity(String username, String password, String domain, String resource)
	{
		assert(username != null && password != null && domain != null && resource != null);
		
		this.username = username;
		this.password = password;
		this.domain = domain;
		this.resource = resource;
	}
	
	public String getEmail()
	{
		return username + "@" + domain;
	}
	
	public String getJID()
	{
		return username + "@" + domain + "/" + resource;
	}
	
	public String getPassword()
	{
		return password;
	}
	
	public void clearPassword()
	{
		password = "";
	}
	
}
