package org.ara.xmpp;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.ara.MPIMMessenger;

public class MpimAuthenticate extends Thread
{
	SocketChannel sc;
	Selector pool;
	String domain;
	ConnectionState state;

	static String id = "++TR84Sm6A3hnt3Q065SnAbbk3Y"; 
	static String streamResponse = "<?xml version='1.0'?>\n\n\n" +
			"<stream:stream " +
			"from=\"127.0.0.1\" " +
			"to=\"hxteam@live.com\" "+
			"version=\"1.0\" " +
			"xml:lang=\"en\" "+
			"xmlns=\"jabber:client\" "+
			"xmlns:xml=\"http://www.w3.org/XML/1998/namespace\" "+
			"xmlns:stream=\"http://etherx.jabber.org/streams\" ";
	static String endStream = "</stream:stream>";


	public MpimAuthenticate(Selector pool, SocketChannel socket)
	{
		this.sc = socket;
		this.pool = pool;
		domain = null;
		state = ConnectionState.DISCONNECTED;
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
		DataOutputStream outToClient;

		try {
			xmlEvents = xmlFactory.createXMLEventReader(new InputStreamReader(sc.socket().getInputStream()));
			outToClient = new DataOutputStream(sc.socket().getOutputStream());

			while(xmlEvents.hasNext()) {
				event = xmlEvents.nextEvent();
				
				//System.out.println("iq_id is: " + iq_id + ", with "+ event);
				
				if(event.isStartElement()) {
					StartElement element = event.asStartElement();
					
					if(element.getName().getLocalPart().equals("stream")) {
						/*TODO: find a way to handle the restart of the stream 
						 *      Also handle TLS negotiation and SASL */
						
						response = streamResponse + "id='" + id + "'>" +
								"<stream:features />";
						
						outToClient.write(response.getBytes());
						
					} else if(element.getName().getLocalPart().equals("iq")) {
						iq_type = element.getAttributeByName(new QName("type")).getValue();
						iq_id = element.getAttributeByName(new QName("id")).getValue();
						domain =  element.getAttributeByName(new QName("to")).getValue();
						
					} else if(element.getName().getLocalPart().equals("username")) {
						event = xmlEvents.nextEvent();
						if(iq_type != null && iq_type.equals("set"))
							username = event.asCharacters().getData(); 
					} /*else if(element.getName().getLocalPart().equals("query")) {
						Attribute attr = element.getAttributeByName(new QName("xmlns"));
						
						String ns = attr.getValue();
						if(ns.equals("jabber:iq:auth"))
							System.out.println("Authenticating");
					} */else if(element.getName().getLocalPart().equals("password")) {
						event = xmlEvents.nextEvent();
						if(iq_type != null && iq_type.equals("set"))
							password = event.asCharacters().getData();
					}
					
				} else if(event.isEndElement()) {
					EndElement element = event.asEndElement();
					if(element.getName().getLocalPart().equals("iq")) {
						if(iq_type.equals("get")) {
							/* Ask for the username, password, and resource */
							response = "<iq type='result' id='" + iq_id +"'>" +
									"<query xmlns='jabber:iq:auth'>"+
									"<username/>" +
									"<password/>" +
									"<resource/>"+
									"<id/>"+
									"</query>"+
									"</iq>";
							
							outToClient.write(response.getBytes());
						} else if(iq_type.equals("set")) {
							MPIMMessenger msn;
							
							if(username == null || domain == null || password == null) {
								response ="<iq type='error' id='" + iq_id + "'>"+
									"	<error type='auth'>"+ 
						          "<not-authorized xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" +
						          "</error></iq>"+
						        "</error></stream:stream>";
								
								outToClient.write(response.getBytes());
								outToClient.flush();
							}
								
							msn = new MPIMMessenger(username + "@" + domain, password, this, sc);
							msn.start();
							goToSleep();
							if(state == ConnectionState.AUTHENTICATED) {
								response ="<iq type='result' id='" + iq_id + "'/>";
								
								outToClient.write(response.getBytes());
								outToClient.flush();
								
								sc.configureBlocking(false);
								sc.register(pool, SelectionKey.OP_READ, msn);
								break;
							} else {
								response ="<iq type='error' id='" + iq_id + "'>"+
									"	<error type='auth'>"+ 
						          "<not-authorized xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" +
						          "</error></iq>"+
						        "</error></stream:stream>";
								outToClient.write(response.getBytes());
								outToClient.flush();
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
