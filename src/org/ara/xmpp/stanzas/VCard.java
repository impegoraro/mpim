package org.ara.xmpp.stanzas;

public class VCard extends Stanza
{
	public VCard(String nickname)
	{
		super("vCard");
		
		assert(nickname != null);
		
		addAttribute("xmlns", "vcard-temp");
		addChild(new Stanza("NICKNAME", false, false).setText(nickname));
	}
	
	public String getNickname()
	{
		
		try {
			return getChildValue("NICKNAME");
		} catch (Exception e) {
		}
		return "";
	}
	
	public Stanza setNickname(String nickname)
	{
		assert(nickname != null);
		
		getChildByName("NICKNAME").setText(nickname);

		return this;
	}
	
	public String getEmail()
	{
		
		try {
			return getChildValue("EMAIL");
		} catch (Exception e) {
		}
		return "";
	}
	
	public Stanza setEmail(String email)
	{
		assert(email != null);
		
		Stanza stanza = getChildByName("EMAIL");
		if(stanza == null) {
			stanza = new Stanza("EMAIL", false, false);
			addChild(stanza);
		}

		stanza.setText(email);
		
		return this;
	}

	public String getAvatar()
	{
		Stanza avatar;
		
		avatar = getChildByName("PHOTO");
		if(avatar != null) 
			return avatar.getChildByName("BINVAL").getChilds();
		
		return "";
	}
	
	public Stanza setAvatar(String base64Photo, String imageType)
	{
		assert(base64Photo != null && imageType != null);
		Stanza avatar;
		Stanza type;
		Stanza binval;
		
		avatar = getChildByName("PHOTO");
		if(avatar == null) {
			avatar = new Stanza("PHOTO");
			type = new Stanza("TYPE", false, false);
			avatar.addChild(type);
			binval = new Stanza("BINVAL", false, false);
			avatar.addChild(binval);
			this.addChild(avatar);
		} else {
			if(avatar.isSimple()) {
				
			}
			type = avatar.getChildByName("TYPE");
			binval = avatar.getChildByName("BINVAL");
		}
		
		assert(type != null && binval != null);
		type.setText(imageType);
		binval.setText(base64Photo);
		
		return this;
	}
	
	public Stanza appendAvatar(String base64)
	{
		assert(base64 != null);
		
		Stanza avatar;
		Stanza binval;
		
		avatar = getChildByName("PHOTO");
		if(avatar == null) {
			avatar = new Stanza("PHOTO");
			avatar.addChild(new Stanza("TYPE", false, false).setText("image/jpeg"));
			binval = new Stanza("BINVAL", false, false);
			avatar.addChild(binval);
			
		} else {
			binval = avatar.getChildByName("BINVAL");
		}
		
		
		binval.setText(binval.getChilds() + base64);
		
		return this;
	}
}
