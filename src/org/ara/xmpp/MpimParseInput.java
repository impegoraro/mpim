package org.ara.xmpp;

import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.ara.MPIMMessenger;

public class MpimParseInput extends Thread
{
	private SocketChannel sc;
	private String parse;
	private MPIMMessenger msn;
	public volatile String msg;
	
	public MpimParseInput(SelectionKey key){
		sc = (SocketChannel) key.channel();
		msn = (MPIMMessenger) key.attachment();
		try{
			ByteBuffer data = ByteBuffer.allocate(sc.socket().getSendBufferSize());
			
			if(sc.read(data) == -1)
				parse = null;
			else
				parse = (new String(data.array())).trim();
			
			if(parse.equals("\0") || parse.length()==0)
				parse = null;
			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run()
	{
		if(parse == null)
			return;
		
		XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
		Reader reader = new StringReader(parse);
		XMLEventReader xmlEvents;
		XMLEvent event;
		
		try {
			
			xmlEvents = xmlFactory.createXMLEventReader(reader);
			
			while(xmlEvents.hasNext()) {
				event = xmlEvents.nextEvent();
				
				if(event.isStartElement()) {
					StartElement evTmp = event.asStartElement();
					
					if(event.asStartElement().getName().getLocalPart().equals("iq")) {
						String id="";
						@SuppressWarnings("unchecked")
						Iterator<Attribute> i= event.asStartElement().getAttributes();
						
						while(i.hasNext()) {
							Attribute attr = i.next();
							
							String name = attr.getName().getLocalPart();
							String value = attr.getValue();

							if(name.equals("id"))
								id = value;
						}
						
						while(xmlEvents.hasNext()) {
							event = xmlEvents.nextEvent();
							String x = null;
							if(event.isStartElement()){
							
								if(event.asStartElement().getName().getLocalPart().equals("query")) {
									Iterator<Namespace> ii= event.asStartElement().getNamespaces();
									
									while(ii.hasNext()) {
										Namespace attr= ii.next();
										
										String value =  attr.getValue();

										if(value.equals("jabber:iq:roster"))
											x = value;
									}
									if(x != null){
										
										System.out.println("sending roster");
										
										msn.sendRoster(id);
									}
									
							}
							
							if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("iq"))
								break;
							}
						}
					} else if(event.asStartElement().getName().getLocalPart().equals("message")) {
						Attribute attrTo = event.asStartElement().getAttributeByName(new QName("to"));
						Attribute attrID = event.asStartElement().getAttributeByName(new QName("id"));
						Attribute attrType = event.asStartElement().getAttributeByName(new QName("type"));
						String msg = "";
						
						if(attrTo == null || attrID == null) { 
							/*TODO: send error to client. */
						} else {
							boolean shouldSend = false;
							boolean composing = false;
							while(xmlEvents.hasNext()) {
								event = xmlEvents.nextEvent();
								
								if(event.isStartElement() && event.asStartElement().getName().getLocalPart().equals("body")){
									shouldSend = true;
									event = xmlEvents.nextEvent();	
									if(event.isCharacters())
										msg = new String(event.asCharacters().getData());									
								
								} else if(event.isStartElement() && event.asStartElement().getName().getLocalPart().equals("composing")) {
									composing = true; 
								} else if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("body")) {
									//System.out.println("Sending message to: " + attrTo.getValue() + ", " + msg);
								
								}else if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("message")) {
									if(attrType.getValue().equals("chat") && shouldSend)
										msn.sendMessage(attrTo.getValue(), msg);
									if(composing)
										msn.sendTyping(attrTo.getValue());
									break;
								}
							}
						}
					}
				}
			}			
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}
	
	}
}