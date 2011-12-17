package org.ara.xmpp;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.SocketException;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import net.sf.jml.util.Base64;

import org.apache.http.ConnectionClosedException;
import org.ara.legacy.LegacyContact;
import org.ara.legacy.LegacyNetwork;
import org.ara.xmpp.stanzas.IQStanza;
import org.ara.xmpp.stanzas.IQStanza.IQType;
import org.ara.xmpp.stanzas.MessageChatStanza;
import org.ara.xmpp.stanzas.MessageStanza;
import org.ara.xmpp.stanzas.PresenceStanza;
import org.ara.xmpp.stanzas.Stanza;
import org.ara.xmpp.stanzas.VCard;

public class MpimParseInput
{
	private String parse;
	private Proxy accounts;
	private String body;
	
	public MpimParseInput(Proxy proxy) throws ConnectionClosedException 
	{
		//assert(parse != null && proxy != null);
		assert(proxy != null);
		String inicial ="<stream>";
		String end = "</stream>";
		String tmp;
		int i;
		
		body = null;
		accounts = proxy;
		try{
			byte data[] = new byte[accounts.getConnection().socket().getSendBufferSize()];

			if(accounts.getConnection().socket().getInputStream().read(data) == -1) {
				// Channel is closed, close the channel and remove the key from the selector
				accounts.close();
				throw new ConnectionClosedException("Connection is closed"); 
			} else {
				parse = inicial;
				tmp = new String(data).trim();
				i = tmp.indexOf("<body>");
				if(i == -1 )
					parse += tmp + end;
				else {
					parse += tmp.substring(0, i + "<body>".length()) + " ";
					body = tmp.substring(i + "<body>".length(), tmp.lastIndexOf("</body>"));
					parse += tmp.substring(tmp.lastIndexOf("</body>")) + end;
					
				}
			}

			if(parse.equals("\0") || parse.length()==0)
				parse = null;

		} catch(NullPointerException e) {
			System.err.println("(EE) Unrecoverable error: the socket is null");
		} catch (SocketException e) {
			try {
				accounts.close();
			} catch(NullPointerException e1) {
			}
				throw new ConnectionClosedException("Connection is closed");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void parse()
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
							else if(attrType.getValue().equals("get"))
								type = IQType.GET;
							else
								type = IQType.ERROR;
							
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
								LegacyNetwork legacy = accounts.getHandle();
								@SuppressWarnings("rawtypes")
								Iterator i = event.asStartElement().getNamespaces();

								namespace = "";
								while(i.hasNext()) {
									Namespace ns = (Namespace) i.next();
									namespace = ns.getValue();
								}

								if(namespace.equals("jabber:iq:roster")) {
									System.out.println("(II) Asking for roster");
									
									/*TODO: remove busy waiting for something better*/
									while(!legacy.contactListReady())
										System.out.print("");
									
									MessageBuilder.sendRoster(accounts.getConnection(), id, accounts.getHandle().getID(), legacy.getContacts());
									for(LegacyContact contact : legacy.getContacts()) {
										try {
											accounts.getConnection().write(MessageBuilder.bluildContactPresenceStanza(contact));
										} catch (IOException e) {
										}
									}
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
										accounts.getConnection().write(iqresult);
									} catch (IOException e) {
									}
								} else {
									System.out.println("(WW) Unsuported namespace: '" + namespace+ "' id is " + id);
								}
							} else if(event.asStartElement().getName().getLocalPart().equals("vCard")) {
								/* Build and send VCard */
								String to = stanza.getAttributeValueByName("to");
								LegacyNetwork handle = accounts.getHandle();
								Stanza iqresult = new IQStanza(IQType.RESULT, stanza.getAttributeValueByName("id"));
								VCard vcard;
								iqresult.addAttribute("from", to);
								
								if(to.equals(handle.getID())) {
									System.out.println("(II) Building self VCard");
									vcard = new VCard(handle.getNickname());
									vcard.setEmail(handle.getID());
									try {
									 vcard.setAvatar(handle.getSelfAvatar(), "image/jpeg");
									} catch(NullPointerException e) {
									}
									iqresult.addChild(vcard);
								} else {
									System.out.println("(II) Building VCard for " + to);
									LegacyContact contact = handle.getContact(to);
									if(contact != null) {
										
										vcard = new VCard(contact.displayName);
										vcard.setEmail(contact.email);
										if(contact.avatar != null) {
											vcard.setAvatar(new String(Base64.encode(contact.avatar )), "image/jpeg");
											System.out.println("(DEBUG) [Network: MSN] download completed successfully");
										} else
											System.err.println("(WW) Avatar unavailable");
										iqresult.addChild(vcard);
									}
								}
								
								try {
									accounts.getConnection().write(iqresult);
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
									try {
										String tmp = parse.substring(parse.indexOf("<BINVAL>")+"<BINVAL>".length(), parse.indexOf("</BINVAL>"));
										vcard.setAvatar(tmp, "image/jpeg");
									} catch(StringIndexOutOfBoundsException e) {
									}
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
							
							LegacyNetwork handle = accounts.getHandle();
							VCard vcard = (VCard) stanzaOut;
							handle.setNickname(vcard.getNickname());
							handle.setAvatar(vcard.getAvatar());
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
						if(event.asStartElement().getName().getLocalPart().equals("show"))
							((PresenceStanza) stanza).setShow(xmlEvents.nextEvent().asCharacters().getData());
						else if(event.asStartElement().getName().getLocalPart().equals("status"))
							((PresenceStanza) stanza).setStatus(xmlEvents.nextEvent().asCharacters().getData());
						
					} else if(event.isEndElement()) {

						if(event.asEndElement().getName().getLocalPart().equals("presence")) {
							String status = null;
							String show = null;
							
							try{
								show = stanza.getChildValue("show");
								status = stanza.getChildValue("status");
							} catch(Exception e){
							}
							
							accounts.getHandle().changedStatus(show, status);
							
							stanza = null;
						}
					}

				} else if(stanza instanceof MessageStanza) {
					if(event.isStartElement()) {
						if(event.asStartElement().getName().getLocalPart().equals("body")) {
							//String msg;
							
							event = xmlEvents.nextEvent();
							//msg = event.asCharacters().getData();
							body = (body == null) ? "" : body;
							((MessageChatStanza) stanza).setBody(body);
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
								accounts.getHandle().sendMessage(stanza.getAttributeValueByName("to"), msg);
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
