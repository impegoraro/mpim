package org.ara.xmpp;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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
import org.ara.xmpp.stanzas.MessageChatStanza;
import org.ara.xmpp.stanzas.PresenceStanza;
import org.ara.xmpp.stanzas.Stanza;

public class MpimParseInput //extends Thread
{
	private SocketChannel sc;
	private String parse;
	private Proxy accounts;
	private StanzaState state;

	public MpimParseInput(SelectionKey key){
		String inicial ="<stream>";
		String end = "</stream>";
		sc = (SocketChannel) key.channel();
		accounts = (Proxy) key.attachment();
		state = StanzaState.CLEAN;
		
		try{
			ByteBuffer data = ByteBuffer.allocate(sc.socket().getSendBufferSize() + inicial.length() + end.length());

			if(sc.read(data) == -1) {
				// Channel is closed, close the channel and remove the key from the selector
				key.cancel();
				accounts.close();
			} else
				parse = inicial + (new String(data.array())).trim() + end;

			if(parse.equals("\0") || parse.length()==0)
				parse = null;

		} catch(ClosedChannelException e) {
			System.out.println("(WW) Closed channel ");
			accounts.close();

		} catch(NullPointerException e) {
			if(sc == null) {
				System.out.println("(EE) Unrecoverable error: the socket is null. Ending the application");
				System.exit(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//@Override
	public void run()
	{
		String id = "";
		Stanza stanza = null;
		
		//System.out.println("Thread " + thid + " is running");

		if(parse == null) {
			/* there's nothing to parse */
			return;
		}

		XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
		Reader reader = new StringReader(parse);
		XMLEventReader xmlEvents;
		XMLEvent event = null;

		try {

			xmlEvents = xmlFactory.createXMLEventReader(reader);

			while(xmlEvents.hasNext()) {
				event = xmlEvents.nextEvent();
				String namespace;

				if(state == StanzaState.CLEAN) {

					if(event.isStartElement()) {
						StartElement evTmp = event.asStartElement();

						if(evTmp.getName().getLocalPart().equals("iq")) {	
							id = evTmp.getAttributeByName(new QName("id")).getValue();
							//stanza = new IQStanza(type, id)
							state = StanzaState.IQ;
							
						} else if(evTmp.getName().getLocalPart().equals("presence")) {
							stanza = new PresenceStanza(null, null);
							
							state = StanzaState.PRECENSE;
							
						} else if(evTmp.getName().getLocalPart().equals("message")) {
							Attribute attrTo = event.asStartElement().getAttributeByName(new QName("to"));

							stanza = new MessageChatStanza(null, attrTo.getValue());
							state = StanzaState.MESSAGE;
						}
					}

				} else if (state == StanzaState.IQ) {
					if(event.isStartElement()) {
						if(event.asStartElement().getName().getLocalPart().equals("query")) {
							MPIMMessenger msn = accounts.getMSN();
							Iterator i = event.asStartElement().getNamespaces();
							
							namespace = "";
							while(i.hasNext()) {
								Namespace ns = (Namespace) i.next();
								namespace = ns.getValue();
							}
							
							if(namespace.equals("jabber:iq:roster")) {
								System.out.println("(II) Asking for roster");

								msn.sendRoster(id);
								msn.setAllowPresence(true);
								msn.sendContactListPresence();

							} else if(namespace.equals("http://jabber.org/protocol/disco#info")) {
								System.out.println("(II) Sending Service unavailable (info)");

								Stanza iqresult = new IQStanza(IQType.RESULT, id);
								Stanza query = new Stanza("query", true);
								Stanza error = new Stanza("error");
								Stanza notAllowd = new Stanza("service-unavailable", true);
								error.addAttribute("type", "cancel");

								query.addAttribute("xmlns", "http://jabber.org/protocol/disco#info");
								query.addAttribute("node", "http://jabber.org/protocol/commands");
								iqresult.addChild(query);
								iqresult.addChild(error);
								notAllowd.addAttribute("xmlns" ,"urn:ietf:params:xml:ns:xmpp-stanzas");
								error.addChild(notAllowd);

								try {
									accounts.getConnection().write(iqresult);
								} catch (IOException e) {
									e.printStackTrace();
								}


							} else if(namespace.equals("http://jabber.org/protocol/disco#items")) {
								System.out.println("(II) Sending Service unavailable (items)");

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
							} else {
								System.out.println("(WW) Unsuported namespace: '" + namespace+ "' id is " + id);
							}
						} else if(event.asStartElement().getName().getLocalPart().equals("ping")) {
							IQStanza pong = new IQStanza(IQType.RESULT, id);

							System.out.println("(II) Sending ping reply...");

							try {
								accounts.getConnection().write(pong);
							} catch (IOException e) {
							}
						}
					} else if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("iq")) {
						state = StanzaState.CLEAN;
						stanza = null;
					}

				} else if(state == StanzaState.PRECENSE) {
				
					if(event.isStartElement()) {						
						if(event.asStartElement().getName().getLocalPart().equals("show") || 
						   event.asStartElement().getName().getLocalPart().equals("status")) {
							
							Stanza tmp = new Stanza(event.asStartElement().getName().getLocalPart(), true);
							tmp.setText(xmlEvents.nextEvent().asCharacters().getData());
							stanza.addChild(tmp);
						}
						
					} else if(event.isEndElement()) {

						if(event.asEndElement().getName().getLocalPart().equals("presence")) {
							String status = null;
							String show = null;
							
							try{
								show = stanza.getChildValue("show");
								status = stanza.getChildValue("status");
							} catch(Exception e){
							}
							
							accounts.getMSN().setStatus((show == null)? "" : show, (status == null)? "" : status);
							
							stanza = null;
							state = StanzaState.CLEAN;
						}
					}
					
				} else if(state == StanzaState.MESSAGE) {
					if(event.isStartElement()) {
						if(event.asStartElement().getName().getLocalPart().equals("body")) {
							String msg; 
							
							event = xmlEvents.nextEvent();
							msg = event.asCharacters().getData();
							((MessageChatStanza) stanza).setBody(msg);
							event = xmlEvents.nextEvent();
						}
						
					} else if(event.isEndElement()) {
						if(event.asEndElement().getName().getLocalPart().equals("message")) {
							if(stanza instanceof MessageChatStanza) {
								String msg = null;
								
								try {
									msg = stanza.getChildValue("body");
								} catch (Exception e) {
								}
								accounts.getMSN().sendMessage(stanza.getAttributeByName("to"), msg);
							}

							state = StanzaState.CLEAN;
							stanza = null;
						}
					}
				}
			}			
		} catch (XMLStreamException e) {
			System.out.println("(EE) parsing error, the last xml was " + event);
			System.out.println("===========================================");
			System.out.println(parse + "\n");
			System.out.println("-------------------------------------------");
			System.out.println("(DEBUG) cause: " + e.getMessage());

			System.out.println("===========================================");
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
			}
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

	private enum StanzaState {
		CLEAN,
		IQ,
		MESSAGE,
		PRECENSE
	}
}
