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
	boolean allowChilds; // True if allows child stanzas otherwise false, allows to use string instead
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
		this.allowChilds = allowChilds;
		this.text = "";
	}

	public Stanza addAttribute(String key, String value)
	{
		assert(key != null && value != null);
		attributes.add(new Pair<String, String>(key, value));
		
		return this;
	}

	public Stanza setNamespace(String namespace)
	{
		assert(namespace != null);
		Pair<String, String> p = getAttributeByName("xmlns");
		
		if(p == null) {
			p = new Pair<String, String>("xmlns", namespace);
		} else 
			p.setSecond(namespace);
		
		return this;
	}
	
	public String getAttributeValueByName(String name) 
	{
		assert(name != null);
		
		for(Pair<String, String> attr : attributes)
			if(attr.getFirst().equals(name))
				return attr.getSecond();
		
		return null;
	}
	
	public Stanza getChildByName(String name) 
	{
		assert(name != null);
		
		for(Stanza stanza : childs)
			if(stanza.name.equals(name))
				return stanza;
		
		return null;
	}
	
	public Stanza addChild(Stanza stanza)
	{
		assert(stanza!= null);
		
		if(!allowChilds)
			return null;
		
		childs.add(stanza);
		return this;
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
		
		if(child.allowChilds)
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
		return startTag() + getChilds() + endTag();
	}
	
	@Override
	public String toString()
	{
		return startTag() + getChilds(true) + endTag(); 
	}
	
	public Stanza setText(String text)
	{
		if(allowChilds)
			return null;
		
		this.text = text;
		return this;
	}
	
	/* Dangerous method, use it if you know what you're doing */
	protected Stanza clearAll(boolean simple, boolean allowChilds)
	{
		attributes = null;
		childs = null;
		text = null;
		this.simple = simple;
		this.allowChilds = allowChilds;
		
		return this;
	}
	
	protected Pair<String, String> getAttributeByName(String name) 
	{
		assert(name != null);
		
		for(Pair<String, String> attr : attributes)
			if(attr.getFirst().equals(name))
				return attr;
		
		return null;
	}

	private String getChilds(boolean newline)
	{
		String stanza = "";
		
		if(!allowChilds)
			stanza = (text == null)? "" : text;
		else {
			for(Stanza c : childs) {
				stanza += (newline ? "\n" : "") + c.startTag() +  c.getChilds(false) + (c.isSimple() ? "" : c.endTag());
			}
		}
		
		stanza += (newline ? "\n" : "");
		return stanza;
	}
}
