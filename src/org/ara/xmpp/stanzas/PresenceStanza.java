package org.ara.xmpp.stanzas;

public class PresenceStanza extends Stanza
{

	public PresenceStanza(String from, String to, String status, String show)
	{
		super("presence");
		
		assert(from != null && to != null && status != null && show != null);
		Stanza sshow;
		Stanza sstatus;
		
		addAttribute("from", from);
		addAttribute("to", to);
		
		sshow = new Stanza("show", false, false);
		sshow.setText(show);
		
		sstatus= new Stanza("status", false, false);
		sstatus.setText(status);
		
		addChild(sshow);
		addChild(sstatus);
	}
	
	public PresenceStanza(String from, String to)
	{
		super("presence");
		
		assert(from != null);
		
		addAttribute("from", from);
		if(to != null) 
			addAttribute("to", to);
		
	}
	
	public void setStatus(String status)
	{
		assert(status != null);
		Stanza sstatus;
		
		sstatus = new Stanza("status", false, false);
		sstatus.setText(status);
		
		addChild(sstatus);
	}
	
	public void setShow(String show)
	{
		assert(show != null);
		Stanza sshow;
		
		sshow = new Stanza("show", false, false);
		sshow.setText(show);
		
		addChild(sshow);
	}
	
	public void setAvatar(String strsha1)
	{
		assert(strsha1 != null);
		
		Stanza avatar;
		Stanza sha1;
		
		avatar = getChildByName("x");
		if(avatar == null) {
			avatar = new Stanza("x").addAttribute("xmlns", "vcard-temp:x:update");
			sha1 = new Stanza("photo", false, false);
			avatar.addChild(sha1);
			
			addChild(avatar);
		} else {
			sha1 = getChildByName("x").getChildByName("photo");
		}
		
		sha1.setText(strsha1);
	}
	
	public void setNoAvatar()
	{		
		Stanza avatar;
		Stanza sha1;
		
		avatar = getChildByName("x");
		if(avatar == null) {
			avatar = new Stanza("x").addAttribute("xmlns", "vcard-temp:x:update");
			sha1 = new Stanza("photo", true, false);
			avatar.addChild(sha1);
			
			addChild(avatar);
		} else {
			sha1 = getChildByName("x").getChildByName("photo").clearAll(true, false);
		}
		
	}
}
