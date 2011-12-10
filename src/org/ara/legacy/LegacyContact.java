package org.ara.legacy;

import java.util.ArrayList;
import java.util.List;

public class LegacyContact
{	
	public String email;
	public String resource;
	public String id;
	public String displayName;
	public String personalMessage;
	public byte[] avatar;
	public String avatarSha1;
	public LegacyUserStatus status;
	public List<String> groups;

	
	public LegacyContact()
	{
		email = null;
		resource = null;
		id = null;
		displayName = null;
		personalMessage = null;
		avatar = null;
		avatarSha1 = null;
		status = LegacyUserStatus.UNAVAILABLE;
		groups = new ArrayList<String>();
	}
	
	public void addGroup(String grpname)
	{
		groups.add(grpname);
	}
}