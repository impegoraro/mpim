package org.ara.xmpp.stanzas;

import java.util.ArrayList;
import java.util.List;

import org.ara.util.Pair;

public class Stanza
{
	private String name;
	List<Pair< String,String> > attributes;
	List<Stanza> childs;
	boolean simple;     // True is the inicial tag is also the close tag, e.g. <foobar/> 
	boolean allowChild; // True if allows child stanzas otherwise false, allows to use string instead
	String text;
	
	public Stanza(String name)
	{
		this(name, false);
	}
	
	public Stanza(String name, boolean simple)
	{
		this(name, simple, true);
	}
	
	public Stanza(String name, boolean simple, boolean allowChilds)
	{
		assert(name != null);
		
		this.name = name;
		this.attributes = new ArrayList< Pair<String, String> >();
		this.childs = new ArrayList<Stanza>();
		this.simple = simple;
		this.allowChild = allowChilds;
		this.text = "";
	}

	public void addAttribute(String key, String value)
	{
		assert(key != null && value != null);
		attributes.add(new Pair<String, String>(key, value));
	}
	
	public String getAttributeByName(String name) 
	{
		assert(name != null);
		
		for(Pair<String, String> attr : attributes)
			if(attr.getFirst().equals(name))
				return attr.getSecond();
		
		return null;
	}
	
	public boolean addChild(Stanza stanza)
	{
		assert(stanza!= null);
		
		if(!allowChild)
			return false;
		
		childs.add(stanza);
		return true;
	}
	
	public String startTag()
	{
		String stanza = "<" + name;
		
		for(Pair<String, String> p : attributes) {
			stanza += " " + p.getFirst() + "='" + p.getSecond() + "'";
		}
		stanza += (simple? " /" : "") + ">";

		return stanza;
	}
	
	public boolean isSimple()
	{
		return simple;
	}
	
	public String getChildValue(String childName) throws Exception
	{
		assert(childName != null);
		
		Stanza child = null;
		
		for(Stanza c : childs) {
			if(c.name.equals(childName)) {
				child = c;
				break;
			}	
		}
		
		if(child == null)
			return null;
		
		if(child.allowChild)
			throw new Exception("Operation not allowed on this child stanza");
		
		return child.text;
	}
	
	public String getChilds()
	{
		return getChilds(false);
	}
		
	public String endTag()
	{
		if(simple) 
			return "";
		return "</" + name + ">";
	}
	
	public String getStanza()
	{
		return startTag() + getChilds() + (isSimple() ? "" : endTag());
	}
	
	@Override
	public String toString()
	{
		return startTag() + "\n" + getChilds(true) + "\n" + endTag(); 
	}
	
	public boolean setText(String text)
	{
		if(allowChild)
			return false;
		
		this.text = text;
		return true;
	}
	
	private String getChilds(boolean newline)
	{
		String stanza = "";
		
		if(!allowChild)
			stanza = (text == null)? "" : text;
		else {
			for(Stanza c : childs) {
				stanza += c.startTag() + (newline ? "\n" : "") + c.getChilds(newline) + (c.isSimple() ? "" : c.endTag());
			}
		}
		
		return stanza;
	}
}
