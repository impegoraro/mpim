package org.ara.xmpp;

import java.io.IOException;
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
import org.ara.xmpp.stanzas.IQStanza;
import org.ara.xmpp.stanzas.IQStanza.IQType;
import org.ara.xmpp.stanzas.Stanza;

public class MpimParseInput extends Thread
{
	private SocketChannel sc;
	private String parse;
	private Proxy accounts;
	public volatile String msg;
	
	public MpimParseInput(SelectionKey key){
		sc = (SocketChannel) key.channel();
		accounts = (Proxy) key.attachment();
		
		try{
			ByteBuffer data = ByteBuffer.allocate(sc.socket().getSendBufferSize());

			if(sc.read(data) == -1)
				parse = null;
			else
				parse = (new String(data.array())).trim();

			if(parse.equals("\0") || parse.length()==0)
				parse = null;
			
		} catch(NullPointerException e) {
			/*if(sc == null) {
				System.out.println("Socket is null");
				System.exit(1);
			}*/
		} catch (Exception e) {
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

					if(evTmp.getName().getLocalPart().equals("iq")) {
						String id ="";

						id = evTmp.getAttributeByName(new QName("id")).getValue();

						while(xmlEvents.hasNext()) {
							event = xmlEvents.nextEvent();
							String x = null;
							String namespace;
							
							if(event.isStartElement()) {
								MPIMMessenger msn = accounts.getMSN();
								
								if(event.asStartElement().getName().getLocalPart().equals("query")) {
									@SuppressWarnings("unchecked")
									Iterator<Namespace> ii= event.asStartElement().getNamespaces();
									Attribute attrNS = event.asStartElement().getAttributeByName(new QName("xmlns"));
									
									namespace = (attrNS != null) ? attrNS.getValue() : "";
									
									while(ii.hasNext()) {
										Namespace attr= ii.next();

										String value =  attr.getValue();
										x = value;
									}
									
									if(x.equals("jabber:iq:roster")) {
										System.out.println("(II) Asking for roster");
										
										msn.sendRoster(id);
										msn.setAllowPresence(true);
										msn.sendContactListPresence();
										
									} else  {//if(namespace.equals("http://jabber.org/protocol/disco#info"))
										System.out.println("(II) Sending Service unavailable");
										Stanza iqresult = new IQStanza(IQType.RESULT, id);
										Stanza query = new Stanza("query", true);
										Stanza error = new Stanza("error");
										Stanza notAllowd = new Stanza("service-unavailable", true);
										error.addAttribute("type", "cancel");
										
										query.addAttribute("xmlns", "http://jabber.org/protocol/disco#items");
										query.addAttribute("node", "http://jabber.org/protocol/commands");
										iqresult.addChild(query);
										iqresult.addChild(error);
										notAllowd.addAttribute("xmlns" ,"urn:ietf:params:xml:ns:xmpp-stanzas");
										error.addChild(notAllowd);
										
										try {
											sc.write(ByteBuffer.wrap(iqresult.getStanza().getBytes()));
										} catch (IOException e) {
											e.printStackTrace();
										}
									}
								}
								
							} else if((event.isStartElement()) && event.asStartElement().getName().getLocalPart().equals("ping")) {
								IQStanza pong = new IQStanza(IQType.RESULT, id);
								try {
									accounts.getConnection().write(pong);
								} catch (IOException e) {
								}
							}else if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("iq"))
								break;
							
						}
						if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("iq"))
							break;
						
					} else if(event.asStartElement().getName().getLocalPart().equals("presence")) {
						String show = null, status = null;
						boolean sh = false, st = false;
						Attribute attr = event.asStartElement().getAttributeByName(new QName("type"));
						
						if(attr != null && attr.getValue().equals("unavailable")) {
							System.out.println("(II) Closing session");
							accounts.close();
							return;
						}
						
						while(xmlEvents.hasNext()) {
							event = xmlEvents.nextEvent();

							if(event.isStartElement()){
								if(event.asStartElement().getName().getLocalPart().equals("show")) {
									sh = true;
								}
								if(event.asStartElement().getName().getLocalPart().equals("status")){ 
									st = true;
								}
							}
							
							if(sh && event.isCharacters()){
								show = event.asCharacters().getData();
								sh = false;
							}
							if(st && event.isCharacters()){
								status = event.asCharacters().getData();
								st = false;
							}
							
							if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("presence")){
								accounts.getMSN().setStatus(show, status);
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
										accounts.getMSN().sendMessage(attrTo.getValue(), msg);
									if(composing)
										accounts.getMSN().sendTyping(attrTo.getValue());
									break;
								}
							}
						}
					} 
					/* TODO: handle the </stream:stream> ending tag */


				}
			}			
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}

	}
	
	public class Monitor
	{
		boolean signaled = false;
		
		public synchronized void goToSleep()
		{
			try {
				if(!signaled)
					this.wait();
			} catch (InterruptedException e) {
			}
			signaled = true;
		}
		
		public synchronized void wakeMePlease()
		{
			signaled = true;
			this.notify();
		}
	}
}
