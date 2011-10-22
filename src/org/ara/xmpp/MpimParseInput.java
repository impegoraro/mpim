package org.ara.xmpp;

import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

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

	public MpimParseInput(SelectionKey key){
		sc = (SocketChannel) key.channel();
		msn = (MPIMMessenger) key.attachment();
		try{
			ByteBuffer data = ByteBuffer.allocate(sc.socket().getSendBufferSize());

			if(sc.read(data) == -1)
				parse = null;
			else
				parse = (new String(data.array())).trim();

			System.out.println("message from jabber client:\n" + parse);
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
						if(event.isEndElement() && event.asEndElement().getName().getLocalPart().equals("iq"))
							break;
						
					}else if(event.asStartElement().getName().getLocalPart().equals("presence")) {
						String show = null, status = null;
						boolean sh = false, st = false;
						
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
								msn.setStatus(show, status);
								break;
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
