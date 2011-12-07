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
import org.ara.xmpp.stanzas.MessageStanza;
import org.ara.xmpp.stanzas.PresenceStanza;
import org.ara.xmpp.stanzas.Stanza;
import org.ara.xmpp.stanzas.VCard;

public class MpimParseInput
{
	private SocketChannel sc;
	private String parse;
	private Proxy accounts;

	public MpimParseInput(SelectionKey key){
		String inicial ="<stream>";
		String end = "</stream>";
		sc = (SocketChannel) key.channel();
		accounts = (Proxy) key.attachment();
		
		/*TODO: find a way to encode html character that are not in the xml code, i. e. in the body tag*/
		
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

	public void run()
	{
		String id = "";
		Stanza stanza = null;
		Stanza stanzaOut = null;
		boolean parsingVCard = false;
		
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

				if(stanza == null) {

					if(event.isStartElement()) {
						StartElement evTmp = event.asStartElement();

						if(evTmp.getName().getLocalPart().equals("iq")) {	
							IQType type;
							
							id = evTmp.getAttributeByName(new QName("id")).getValue();
							Attribute attrType  = evTmp.getAttributeByName(new QName("type"));
							Attribute attrFrom  = evTmp.getAttributeByName(new QName("from"));
							Attribute attrTo  = evTmp.getAttributeByName(new QName("to"));
							
							if(attrType == null) {
								/* TODO: return an error bad stanza*/
								break;
							}
							
							if(attrType.getValue().equals("set"))
								type = IQType.SET;
							else 
								type = IQType.GET;
							
							stanza = new IQStanza(type, id);
							
							if(attrFrom != null) 
								stanza.addAttribute("from", attrFrom.getValue());
							
							if(attrTo != null) 
								stanza.addAttribute("to", attrTo.getValue());
							
						} else if(evTmp.getName().getLocalPart().equals("presence")) {
							stanza = new PresenceStanza(null, null);
							
						} else if(evTmp.getName().getLocalPart().equals("message")) {
							Attribute attrTo = event.asStartElement().getAttributeByName(new QName("to"));

							stanza = new MessageChatStanza(null, attrTo.getValue());
						}
					}

				} else if (stanza instanceof IQStanza) {
					if(event.isStartElement()) {
						if(stanza.getAttributeValueByName("type").equals("get")) {
							if(event.asStartElement().getName().getLocalPart().equals("query")) {
								MPIMMessenger msn = accounts.getMSN();
								@SuppressWarnings("rawtypes")
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

								} else if(namespace.equals("jabber:iq:version")) {
									Stanza version = new IQStanza(IQType.RESULT, id);
									Stanza query = new Stanza("query").addAttribute("xml", "jabber:iq:version");
									String osinfo;
									
									osinfo= System.getProperty("os.name") + " " + System.getProperty("os.version");
									
									query.addChild(new Stanza("name", false, false).setText(MpimCore.getProgName()));
									query.addChild(new Stanza("version", false, false).setText(MpimCore.getVersion()));
									query.addChild(new Stanza("os", false, false).setText(osinfo));
									
									version.addChild(query);
									
									try {
										accounts.getConnection().write(version);
									} catch (IOException e) {
									}
									
								}else if(namespace.equals("http://jabber.org/protocol/disco#info")) {
									System.out.println("(II) Sending Service Discovery (info)");

									Stanza iqresult = new IQStanza(IQType.RESULT, id);
									Stanza query = new Stanza("query");
									
									query.addChild(new Stanza("feature").addAttribute("var", "jabber:iq:version"));
									query.addChild(new Stanza("feature").addAttribute("var", "vcard-temp"));
									iqresult.addChild(query);

									/*Stanza error = new Stanza("error");
									Stanza notAllowd = new Stanza("service-unavailable", true);
									error.addAttribute("type", "cancel");

									query.addAttribute("xmlns", "http://jabber.org/protocol/disco#info");
									query.addAttribute("node", "http://jabber.org/protocol/commands");
									iqresult.addChild(query);
									iqresult.addChild(error);
									notAllowd.addAttribute("xmlns" ,"urn:ietf:params:xml:ns:xmpp-stanzas");
									error.addChild(notAllowd);*/
									
									try {
										accounts.getConnection().write(iqresult);
									} catch (IOException e) {
									}


								} else if(namespace.equals("http://jabber.org/protocol/disco#items")) {
									System.out.println("(II) Sending Service Discovery (items)");

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
							} else if(event.asStartElement().getName().getLocalPart().equals("vCard")) {
								/* Build and send VCard */
								
								Stanza iqvcard = new IQStanza(IQType.RESULT, stanza.getAttributeValueByName("id"));;
								String to = stanza.getAttributeValueByName("to");

								VCard vcard = accounts.getMSN().createVCard(to);
								iqvcard.addAttribute("from", to);
								
								if(vcard != null)
									iqvcard.addChild(vcard);
								else 
									System.err.println("(EE) failed to create a vcard for user '" + to+ "'");
								
								try {
									accounts.getConnection().write(iqvcard);
								} catch (IOException e) {
								}
								
							} else if(event.asStartElement().getName().getLocalPart().equals("ping")) {
								IQStanza pong = new IQStanza(IQType.RESULT, id);

								System.out.println("(II) Sending ping reply...");

								try {
									accounts.getConnection().write(pong);
								} catch (IOException e) {
								}
							}
						} else if(stanza.getAttributeValueByName("type").equals("set")) {
							StartElement tag = event.asStartElement();
							
							if(parsingVCard || tag.getName().getLocalPart().equals("vCard")) {
								parsingVCard = true;
								
								if(stanzaOut == null)
									stanzaOut = new VCard("");
								
								VCard vcard = (VCard) stanzaOut;
								if(tag.getName().getLocalPart().equals("NICKNAME")) {
									
									event = xmlEvents.nextEvent();
									vcard.setNickname(event.asCharacters().getData());
								} else if(tag.getName().getLocalPart().equals("EMAIL")) {
									event = xmlEvents.nextEvent();
									vcard.setEmail(event.asCharacters().getData());
								} else if(tag.getName().getLocalPart().equals("BINVAL")) {
									event = xmlEvents.nextEvent();
									String tmp = event.asCharacters().getData();
									vcard.setAvatar(tmp, "image/jpeg");
								}
							}
						} else {
							/* TODO: Bad IQ type, reply with correct error. */
						}
					} else if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("iq")) {
						
						if(stanzaOut != null) {
							Stanza iqresult = new IQStanza(IQType.RESULT, stanza.getAttributeValueByName("id"), true);
							String to = stanza.getAttributeValueByName("from");
							
							if(to != null)
								iqresult.addAttribute("from", to);
							
							MPIMMessenger handle = accounts.getMSN();
							VCard vcard = (VCard) stanzaOut;
							handle.setAccountName(vcard.getNickname());
							handle.setDisplayPicture(vcard.getAvatar());
							stanzaOut = null;
							
							try {
								accounts.getConnection().write(iqresult);
							} catch (IOException e) {
							}
						}

						stanza = null;
					}

				} else if(stanza instanceof  PresenceStanza) {
				
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
						}
					}

				} else if(stanza instanceof MessageStanza) {
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
								accounts.getMSN().sendMessage(stanza.getAttributeValueByName("to"), msg);
							}

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
}
