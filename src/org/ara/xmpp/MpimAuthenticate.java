package org.ara.xmpp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.ara.MPIMMessenger;
import org.ara.xmpp.stanzas.Stanza;

public class MpimAuthenticate extends Thread
{
	SocketChannel sc;
	Selector pool;
	String domain;
	ConnectionState state;
	Stanza stream;

	public MpimAuthenticate(Selector pool, SocketChannel socket)
	{
		this.sc = socket;
		this.pool = pool;
		domain = null;
		state = ConnectionState.DISCONNECTED;
		stream = new Stanza("stream:stream");
		//id='++TR84Sm6A3hnt3Q065SnAbbk3Y'
		stream.addAttribute("version", "1.0");
		stream.addAttribute("xml:lang", "en");
		stream.addAttribute("xmlns", "jabber:client");
		stream.addAttribute("xmlns:xml", "http://www.w3.org/XML/1998/namespace");
		stream.addAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
	}

	@Override
	public void run()
	{
		XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
		XMLEventReader xmlEvents;
		XMLEvent event;
		String username = null;
		String password = null;
		String response;
		String iq_id = "";
		String iq_type = null;
		
		try {
			xmlEvents = xmlFactory.createXMLEventReader(new InputStreamReader(sc.socket().getInputStream()));
			
			while(xmlEvents.hasNext()) {
				event = xmlEvents.nextEvent();
				
				if(event.isStartElement()) {
					StartElement element = event.asStartElement();			
					
					if(element.getName().getLocalPart().equals("stream")) {
						/*TODO: find a way to handle the restart of the stream 
						 *      Also handle TLS negotiation and SASL */
			
						domain = element.getAttributeByName(new QName("to")).getValue();
						
						stream.addAttribute("to", domain);
						stream.addChild(new Stanza("stream:features", true));
						
						sc.write(ByteBuffer.wrap(("<?xml version='1.0' ?>").getBytes()));
						sc.write(ByteBuffer.wrap((stream.startTag() + stream.getChilds()).getBytes()));
						
					} else if(element.getName().getLocalPart().equals("iq")) {
						iq_type = element.getAttributeByName(new QName("type")).getValue();
						iq_id = element.getAttributeByName(new QName("id")).getValue();
						domain =  element.getAttributeByName(new QName("to")).getValue();
						
					} else if(element.getName().getLocalPart().equals("username")) {
						event = xmlEvents.nextEvent();
						if(iq_type != null && iq_type.equals("set"))
							username = event.asCharacters().getData(); 
					} else if(element.getName().getLocalPart().equals("resource")) {
						
					} else if(element.getName().getLocalPart().equals("query")) {
						//Attribute attr = element.getAttributeByName(new QName("xmlns"));
						String value = "";
						Iterator<Namespace> ii = event.asStartElement().getNamespaces();

						while(ii.hasNext()) {
							Namespace attr= ii.next();

							value =  attr.getValue();
							if(!value.equals("jabber:iq:auth") && state != ConnectionState.AUTHENTICATING) {
								
							}
								
							
						}
						if(value.equals("jabber:iq:auth"))
							state = ConnectionState.AUTHENTICATING;
						
					} else if(element.getName().getLocalPart().equals("password")) {
						event = xmlEvents.nextEvent();
						if(iq_type != null && iq_type.equals("set"))
							password = event.asCharacters().getData();
					} else {
						/* The client has send a stanza that was not allowed at this staged */
						Stanza error = new Stanza("stream:error");
						Stanza notAuth = new Stanza("not-authorized");
						
						notAuth.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-streams");
						error.addChild(notAuth);

						sc.write(ByteBuffer.wrap(error.getStanza().getBytes()));
						sc.write(ByteBuffer.wrap(stream.endTag().getBytes()));
						sc.close();
						
						break;
					}
					
				} else if(event.isEndElement()) {
					EndElement element = event.asEndElement();
					if(element.getName().getLocalPart().equals("iq")) {
						if(iq_type.equals("get")) {
							/* Ask for the username, password, and resource */
							Stanza iq = new Stanza("iq");
							Stanza query = new Stanza("query");
							
							iq.addAttribute("id", iq_id);
							iq.addAttribute("type", "result");
							
							query.addAttribute("xmlns", "jabber:iq:auth");
							query.addChild(new Stanza("username", true));
							query.addChild(new Stanza("password", true));
							query.addChild(new Stanza("resource", true));
							
							iq.addChild(query);
														
							sc.write(ByteBuffer.wrap(iq.getStanza().getBytes()));
						} else if(iq_type.equals("set")) {
							MPIMMessenger msn;
							
							if(username == null || domain == null || password == null) {
								response ="<iq type='error' id='" + iq_id + "'>"+
									"<error type='auth'>"+ 
									"<not-authorized xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" +
									"</error></iq></error></stream:stream>";
								
								sc.write(ByteBuffer.wrap(response.getBytes()));
							}
								
							msn = new MPIMMessenger(username + "@" + domain, password, this, sc);
							msn.start();
							goToSleep();
							if(state == ConnectionState.AUTHENTICATED) {
								response ="<iq type='result' id='" + iq_id + "'/>";
								
								sc.write(ByteBuffer.wrap(response.getBytes()));
								
								sc.configureBlocking(false);
								sc.register(pool, SelectionKey.OP_READ, msn);
								break;
							} else {
								Stanza iq = new Stanza("iq");
								Stanza error = new Stanza("error");
								Stanza notAuth = new Stanza("not-authorized", true);
								
								iq.addAttribute("type", "error");
								error.addAttribute("type", "auth");
								notAuth.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
								error.addChild(notAuth);
								iq.addChild(error);
								
								response ="<iq type='error' id='" + iq_id + "'>"+
									"	<error type='auth'>"+ 
						          "<not-authorized xmlns=''/>" +
						          "</error></iq>"+
						        "</error></stream:stream>";
								sc.write(ByteBuffer.wrap(iq.getStanza().getBytes()));
								sc.write(ByteBuffer.wrap(stream.getStanza().getBytes()));
							}
							
						}
					}
				}
			}

		} catch (XMLStreamException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void goToSleep()
	{
		try {
			this.wait();
		} catch (InterruptedException e) {
		}

	}

	public synchronized void wakeMePlease()
	{
		this.notify();
	}
	
	public void setState(ConnectionState state) {
		this.state = state;  
	}
}
