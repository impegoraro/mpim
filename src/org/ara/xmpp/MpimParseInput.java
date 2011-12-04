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
		String old = null;
		
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
									
									query.addChild(new Stanza("name", false, false).setText(MPIMCore.getProgName()));
									query.addChild(new Stanza("version", false, false).setText(MPIMCore.getVersion()));
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
								/* Build and send vcard */
								Stanza iqvcard = new IQStanza(IQType.RESULT, stanza.getAttributeValueByName("id"));;
								String to = stanza.getAttributeValueByName("to");

								/*if(to == null || to.equals(accounts.getConnection().getBareJID())) {
									System.out.println("(II) Creating Self vCard");
									
									//String tmpphoto = "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AAAABgAAAAAAAThCSU0D+AAAAAAAcAAA/////////////////////////////wPoAAAAAP////////////////////////////8D6AAAAAD/////////////////////////////A+gAAAAA/////////////////////////////wPoAAA4QklNBAgAAAAAABAAAAABAAACQAAAAkAAAAAAOEJJTQQeAAAAAAAEAAAAADhCSU0EGgAAAAADQQAAAAYAAAAAAAAAAAAAAJYAAACWAAAABgBTAGsAeQByAGkAbQAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAlgAAAJYAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAQAAAAAAAG51bGwAAAACAAAABmJvdW5kc09iamMAAAABAAAAAAAAUmN0MQAAAAQAAAAAVG9wIGxvbmcAAAAAAAAAAExlZnRsb25nAAAAAAAAAABCdG9tbG9uZwAAAJYAAAAAUmdodGxvbmcAAACWAAAABnNsaWNlc1ZsTHMAAAABT2JqYwAAAAEAAAAAAAVzbGljZQAAABIAAAAHc2xpY2VJRGxvbmcAAAAAAAAAB2dyb3VwSURsb25nAAAAAAAAAAZvcmlnaW5lbnVtAAAADEVTbGljZU9yaWdpbgAAAA1hdXRvR2VuZXJhdGVkAAAAAFR5cGVlbnVtAAAACkVTbGljZVR5cGUAAAAASW1nIAAAAAZib3VuZHNPYmpjAAAAAQAAAAAAAFJjdDEAAAAEAAAAAFRvcCBsb25nAAAAAAAAAABMZWZ0bG9uZwAAAAAAAAAAQnRvbWxvbmcAAACWAAAAAFJnaHRsb25nAAAAlgAAAAN1cmxURVhUAAAAAQAAAAAAAG51bGxURVhUAAAAAQAAAAAAAE1zZ2VURVhUAAAAAQAAAAAABmFsdFRhZ1RFWFQAAAABAAAAAAAOY2VsbFRleHRJc0hUTUxib29sAQAAAAhjZWxsVGV4dFRFWFQAAAABAAAAAAAJaG9yekFsaWduZW51bQAAAA9FU2xpY2VIb3J6QWxpZ24AAAAHZGVmYXVsdAAAAAl2ZXJ0QWxpZ25lbnVtAAAAD0VTbGljZVZlcnRBbGlnbgAAAAdkZWZhdWx0AAAAC2JnQ29sb3JUeXBl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IyI+IDxyZGY6RGVzY3JpcHRpb24gcmRmOmFib3V0PSIiIHhtbG5zOmRjPSJodHRwOi8vcHVybC5vcmcvZGMvZWxlbWVudHMvMS4xLyIgeG1sbnM6eGFwPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4YXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtbG5zOnBob3Rvc2hvcD0iaHR0cDovL25zLmFkb2JlLmNvbS9waG90b3Nob3AvMS4wLyIgeG1sbnM6dGlmZj0iaHR0cDovL25zLmFkb2JlLmNvbS90aWZmLzEuMC8iIHhtbG5zOmV4aWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20vZXhpZi8xLjAvIiBkYzpmb3JtYXQ9ImltYWdlL2pwZWciIHhhcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIEVsZW1lbnRzIDYuMCBXaW5kb3dzIiB4YXA6Q3JlYXRlRGF0ZT0iMjAxMS0wNC0wM1QwODoyMC0wNTowMCIgeGFwOk1vZGlmeURhdGU9IjIwMTEtMDQtMDNUMDg6MjE6MjEtMDU6MDAiIHhhcDpNZXRhZGF0YURhdGU9IjIwMTEtMDQtMDNUMDg6MjE6MjEtMDU6MDAiIHhhcE1NOkRvY3VtZW50SUQ9InV1aWQ6NTg5QjRCRTFGNDVERTAxMTk0QzhBODQzM0UwN0VDQzMiIHhhcE1NOkluc3RhbmNlSUQ9InV1aWQ6MEJCNTJENDRGNTVERTAxMTk0QzhBODQzM0UwN0VDQzMiIHBob3Rvc2hvcDpDb2xvck1vZGU9IjMiIHBob3Rvc2hvcDpJQ0NQcm9maWxlPSJzUkdCIElFQzYxOTY2LTIuMSIgcGhvdG9zaG9wOkhpc3Rvcnk9IiIgdGlmZjpPcmllbnRhdGlvbj0iMSIgdGlmZjpYUmVzb2x1dGlvbj0iNzIwMDAwLzEwMDAwIiB0aWZmOllSZXNvbHV0aW9uPSI3MjAwMDAvMTAwMDAiIHRpZmY6UmVzb2x1dGlvblVuaXQ9IjIiIHRpZmY6TmF0aXZlRGlnZXN0PSIyNTYsMjU3LDI1OCwyNTksMjYyLDI3NCwyNzcsMjg0LDUzMCw1MzEsMjgyLDI4MywyOTYsMzAxLDMxOCwzMTksNTI5LDUzMiwzMDYsMjcwLDI3MSwyNzIsMzA1LDMxNSwzMzQzMjs3NDY2QkNEQThDNkZFNTVBMDIyNDg0QkFBQjg2NEQxMyIgZXhpZjpQaXhlbFhEaW1lbnNpb249IjE1MCIgZXhpZjpQaXhlbFlEaW1lbnNpb249IjE1MCIgZXhpZjpDb2xvclNwYWNlPSIxIiBleGlmOk5hdGl2ZURpZ2VzdD0iMzY4NjQsNDA5NjAsNDA5NjEsMzcxMjEsMzcxMjIsNDA5NjIsNDA5NjMsMzc1MTAsNDA5NjQsMzY4NjcsMzY4NjgsMzM0MzQsMzM0MzcsMzQ4NTAsMzQ4NTIsMzQ4NTUsMzQ4NTYsMzczNzcsMzczNzgsMzczNzksMzczODAsMzczODEsMzczODIsMzczODMsMzczODQsMzczODUsMzczODYsMzczOTYsNDE0ODMsNDE0ODQsNDE0ODYsNDE0ODcsNDE0ODgsNDE0OTIsNDE0OTMsNDE0OTUsNDE3MjgsNDE3MjksNDE3MzAsNDE5ODUsNDE5ODYsNDE5ODcsNDE5ODgsNDE5ODksNDE5OTAsNDE5OTEsNDE5OTIsNDE5OTMsNDE5OTQsNDE5OTUsNDE5OTYsNDIwMTYsMCwyLDQsNSw2LDcsOCw5LDEwLDExLDEyLDEzLDE0LDE1LDE2LDE3LDE4LDIwLDIyLDIzLDI0LDI1LDI2LDI3LDI4LDMwO0I1NDE2MjgxRURDNzc3NUIzMUE0QjUyNTMwQzEzRkNBIj4gPHhhcE1NOkRlcml2ZWRGcm9tIHN0UmVmOmluc3RhbmNlSUQ9InV1aWQ6MDc1ODk1NTRGNDVERTAxMTk0QzhBODQzM0UwN0VDQzMiIHN0UmVmOmRvY3VtZW50SUQ9InV1aWQ6MDc1ODk1NTRGNDVERTAxMTk0QzhBODQzM0UwN0VDQzMiLz4gPC9yZGY6RGVzY3JpcHRpb24+IDwvcmRmOlJERj4gPC94OnhtcG1ldGE+ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg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";
									VCard vcard;
									
									String nickname = accounts.getMSN().getAccountName();
									vcard = new VCard(nickname);
									
									try {
										vcard.setAvatar(accounts.getMSN().getDisplayPictureBase64(), "image/jpeg");
									} catch(NullPointerException e) {
									}
									
									iqvcard.addChild(vcard);
									
								} else {*/
									VCard vcard = accounts.getMSN().createVCard(to);
									iqvcard.addAttribute("from", to);
									
									if(vcard != null)
										iqvcard.addChild(vcard);
									else 
										System.err.println("(EE) failed to create a vcard for user '" + to+ "'");
								//}
								
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
								old = tag.getName().getLocalPart();
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
					//} else if(event.isCharacters() && old != null && old.equals("BINVAL")) {
						//((VCard)stanzaOut).appendAvatar(event.asCharacters().getData());
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
