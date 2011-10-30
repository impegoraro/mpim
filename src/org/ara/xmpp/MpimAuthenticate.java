package org.ara.xmpp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Random;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
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
		String iq_id = "";
		String iq_type = null;
		String username = null;
		String password = null;
		String resource = null;
		
		try {
			xmlEvents = xmlFactory.createXMLEventReader(new InputStreamReader(sc.socket().getInputStream()));

			while(xmlEvents.hasNext()) {
				event = xmlEvents.nextEvent();

				if(state == ConnectionState.DISCONNECTED) {
					/* Accept only <stream:stream> */
					if(event.isStartElement()) {
						StartElement element = event.asStartElement();

						/*TODO: find a way to handle the restart of the stream 
						 *      Also handle TLS negotiation and SASL */
						if(element.getName().getLocalPart().equals("stream")) {
							domain = element.getAttributeByName(new QName("to")).getValue();
							Attribute attr = element.getAttributeByName(new QName("to"));
							String version = "";
							
							if(attr != null)
								version = attr.getValue();
							
							Random r = new Random();
							Calendar d = Calendar.getInstance();
							r.setSeed(d.getTimeInMillis());
							
							stream.addAttribute("to", domain);
							stream.addAttribute("id", "" + r.nextLong());
							stream.addChild(new Stanza("stream:features", true));

							sc.write(ByteBuffer.wrap(("<?xml version='1.0' ?>").getBytes()));
							sc.write(ByteBuffer.wrap((stream.startTag() + stream.getChilds()).getBytes()));

							if(!version.equals("1.0")) {
								/* TODO: Send incompatible version back to the initiating entity and finish the connection */
								
							}

							state = ConnectionState.AUTHENTICATING;
						} else {
							Stanza error = new Stanza("stream:error");
							Stanza echild = new Stanza("invalid-xml", true);
							echild.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-streams");
							error.addChild(echild);
							
							sc.write(ByteBuffer.wrap(error.getStanza().getBytes()));
							sc.write(ByteBuffer.wrap(stream.endTag().getBytes()));
							sc.close();
							break;
						}

					}

				} else if(state == ConnectionState.AUTHENTICATING) {
					MPIMMessenger msn = null;
					
					if(event.isStartElement()) {
						StartElement element = event.asStartElement();
						
						if(element.getName().getLocalPart().equals("iq")) {
							iq_type = element.getAttributeByName(new QName("type")).getValue();
							iq_id = element.getAttributeByName(new QName("id")).getValue();
							//domain =  element.getAttributeByName(new QName("to")).getValue();
							
							/*TODO: Should check if the type is right */
							
						} else if(element.getName().getLocalPart().equals("query")) {
							//Attribute attr = element.getAttributeByName(new QName("xmlns"));
							String value = "";
							Iterator<Namespace> ii = event.asStartElement().getNamespaces();

							while(ii.hasNext()) {
								Namespace attr= ii.next();

								value =  attr.getValue();
								if(!value.equals("jabber:iq:auth") && state != ConnectionState.AUTHENTICATING) {
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
							}
							if(value.equals("jabber:iq:auth"))
								state = ConnectionState.AUTHENTICATING;
							else // wrong namespace for the iq stanza
								break;
							

						} else if(element.getName().getLocalPart().equals("username")) {
							event = xmlEvents.nextEvent();
							if(iq_type != null && iq_type.equals("set"))
								username = event.asCharacters().getData();

						} else if(element.getName().getLocalPart().equals("resource")) {
							event = xmlEvents.nextEvent();
							if(iq_type != null && iq_type.equals("set"))
								resource = event.asCharacters().getData();

						} else if(element.getName().getLocalPart().equals("password")) {
							event = xmlEvents.nextEvent();
							if(iq_type != null && iq_type.equals("set"))
								password = event.asCharacters().getData();
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
								XMPPConnection con;
								
								if(username == null || domain == null || password == null || resource == null) {
									System.err.println("(EE) Either the username, domain or password is empty");

									Stanza iq_error = new Stanza("iq");
									Stanza error = new Stanza("error");
									Stanza conflict= new Stanza("conflict", true);
									
									error.addAttribute("code", "409");
									error.addAttribute("type", "cancel");
									conflict.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
									
									error.addChild(conflict);
									
									iq_error.addAttribute("type", "error");
									iq_error.addChild(error);
									
									sc.write(ByteBuffer.wrap(iq_error.getStanza().getBytes()));
									sc.write(ByteBuffer.wrap(stream.getStanza().getBytes()));
									sc.close();
									break;
								}
								con = new XMPPConnection(sc, username, password, domain, resource);
								msn = new MPIMMessenger(con.getBareJID(), password, this, con);
								msn.start();

								// should wait to see whether the login was successful 
								goToSleep();
								if(state == ConnectionState.AUTHENTICATED && msn != null) {
									Stanza iqSuccess = new Stanza("iq", true);
									
									iqSuccess.addAttribute("type", "result");
									iqSuccess.addAttribute("id", iq_id);
									sc.write(ByteBuffer.wrap(iqSuccess.getStanza().getBytes()));
									
									sc.configureBlocking(false);
									sc.register(pool, SelectionKey.OP_READ, new Proxy(con, msn));
									break;
									
								} else if(state == ConnectionState.NONAUTHENTICATED) {
									Stanza iq = new Stanza("iq");
									Stanza error = new Stanza("error");
									Stanza notAuth = new Stanza("not-authorized", true);
									
									iq.addAttribute("type", "error");
									error.addAttribute("type", "auth");
									notAuth.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
									error.addChild(notAuth);
									iq.addChild(error);
									
									sc.write(ByteBuffer.wrap(iq.getStanza().getBytes()));
									sc.write(ByteBuffer.wrap(stream.getStanza().getBytes()));
									sc.close();
									break;
								}

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
