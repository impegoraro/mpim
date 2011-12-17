package org.ara.xmpp;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Random;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang.StringEscapeUtils;
import org.ara.legacy.ContactListCallbacks;
import org.ara.legacy.LegacyContact;
import org.ara.legacy.LegacyNetwork;
import org.ara.legacy.LoginCallbacks;
import org.ara.legacy.LoginResult;
import org.ara.legacy.MessageCallbacks;
import org.ara.legacy.msn.LegacyMsn;
import org.ara.xmpp.stanzas.MessageChatStanza;
import org.ara.xmpp.stanzas.Stanza;

public class MpimAuthenticate extends Thread
{
	SocketChannel sc;
	boolean restart;
	boolean useTLS;
	Selector pool;
	String domain;
	ConnectionState state;
	Stanza stream;
	String keyStore = "/home/ilan/Escritorio/cert/mpim.jks";
	String certificateChain = "/home/ilan/Escritorio/cert/mpim.crt";
	String privateKey = "/home/ilan/Escritorio/cert/mpim.key";
	char certPassword[] = "h2r3x_rul3s".toCharArray();

	public MpimAuthenticate(Selector pool, SocketChannel socket, boolean useTLS)
	{
		this.sc = socket;
		this.pool = pool;
		restart = false;
		this.useTLS = useTLS;
		domain = null;
		state = ConnectionState.DISCONNECTED;
		stream = new Stanza("stream:stream");
		stream.addAttribute("version", "1.0");
		stream.addAttribute("xml:lang", "en");
		stream.addAttribute("xmlns", "jabber:client");
		stream.addAttribute("xmlns:xml", "http://www.w3.org/XML/1998/namespace");
		stream.addAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
	}

	public MpimAuthenticate(Selector pool, SocketChannel socket)
	{
		this(pool, socket, false);
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

							if(useTLS) {
								System.out.println("(II) TLS Has been activated");
								if(restart) {
									System.out.println("(II) The connection stream will be restarted");
									stream.addChild(new Stanza("stream:features", true));
									state = ConnectionState.AUTHENTICATING;	
								} else {
									System.out.println("(II) TLS is not activated");
									
									Stanza features = new Stanza("stream:features");
									Stanza tls = new Stanza("starttls");
									
									tls.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
									tls.addChild(new Stanza("required", true));
									features.addChild(tls);
									stream.addChild(features);
									state = ConnectionState.STARTTLS;
								}
							} else {
								stream.addChild(new Stanza("stream:features", true));
								state = ConnectionState.AUTHENTICATING;
							}
							sc.write(ByteBuffer.wrap(("<?xml version='1.0' ?>").getBytes()));
							sc.write(ByteBuffer.wrap((stream.startTag() + stream.getChilds()).getBytes()));

							if(!version.equals("1.0")) {
								/* TODO: Send incompatible version back to the initiating entity and finish the connection */

							}

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
					LegacyNetwork legacy = null;

					if(event.isStartElement()) {
						StartElement element = event.asStartElement();

						if(element.getName().getLocalPart().equals("iq")) {

							iq_type = getAttributeValue(element, "type");
							iq_id = getAttributeValue(element, "id");
							domain =  getAttributeValue(element, "to");

							/*TODO: Should check if the type is right */

						} else if(element.getName().getLocalPart().equals("query")) {
							@SuppressWarnings("unchecked")
							Iterator<Namespace> ii = event.asStartElement().getNamespaces();
							String value = "";

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
							else // TODO: wrong namespace for the iq stanza, send error message and close the stream
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
								legacy = new LegacyMsn();
								legacy.login(con.getBareJID(), password);
								legacy.addCallbacks(new LoginHandler(this));
								legacy.addCallbacks(new MessageHandler(con));
								legacy.addCallbacks(new ContactListHandler(con));

								// should wait to see whether the login was successful 
								goToSleep();
								if(state == ConnectionState.AUTHENTICATED && legacy != null) {
									Stanza iqSuccess = new Stanza("iq", true);

									iqSuccess.addAttribute("type", "result");
									iqSuccess.addAttribute("id", iq_id);
									sc.write(ByteBuffer.wrap(iqSuccess.getStanza().getBytes()));

									sc.configureBlocking(false);
									sc.register(pool, SelectionKey.OP_READ, new Proxy(con, legacy));
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
					// End of AUTHENTICATING
				} else if(state == ConnectionState.STARTTLS) {
					if(event.isStartElement()) {
						if(event.asStartElement().getName().getLocalPart().equals("starttls")) {
							Socket socket = sc.socket();
							/* TODO: Check for the valid namespace */
							Stanza proceed = new Stanza("proceed", true);
							proceed.addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");

							sc.write(ByteBuffer.wrap(proceed.getStanza().getBytes()));
							
							try {
								SSLSocketFactory ssf = null;
								SSLContext ctx = SSLContext.getInstance( "TLS" );
								KeyManagerFactory kmf = KeyManagerFactory.getInstance( "SunX509" );
								KeyStore ks = KeyStore.getInstance("JKS") ;
								ks.load(new FileInputStream(keyStore), certPassword);
								kmf.init(ks, certPassword);
								TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
								tmf.init( ks );
								ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
								ssf = ctx.getSocketFactory();


								socket = ssf.createSocket(sc.socket(), sc.socket().getInetAddress().getHostAddress(), sc.socket().getPort(), true);
								((SSLSocket) socket).setUseClientMode(false);
								sc = socket.getChannel();

							} catch (CertificateException e) {
								e.printStackTrace();
							} catch (KeyManagementException e) {
								e.printStackTrace();
							} catch (UnrecoverableKeyException e) {
								e.printStackTrace();
							} catch (NoSuchAlgorithmException e) {
								e.printStackTrace();
							} catch (KeyStoreException e) {
								e.printStackTrace();
							}

							//client = (SSLSocket) ssf.createSocket(socket, socket.getLocalAddress().getHostAddress(), socket.getPort(), true);

							xmlEvents = xmlFactory.createXMLEventReader(new InputStreamReader(sc.socket().getInputStream()));
							state = ConnectionState.DISCONNECTED;
							restart = true;
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

	private static String getAttributeValue(StartElement element, String name)
	{
		String value = null;
		Attribute attr = element.getAttributeByName(new QName(name));

		if(attr != null)
			value = attr.getValue();

		return value;
	}

	/* Definition of the Handlers */
	private class LoginHandler implements LoginCallbacks
	{
		private MpimAuthenticate mpimAuth;

		public LoginHandler(MpimAuthenticate auth)
		{
			assert(auth != null);
			mpimAuth = auth;
		}

		@Override
		public void loginCompleted(LoginResult result)
		{
			mpimAuth.setState(ConnectionState.AUTHENTICATED);
			mpimAuth.wakeMePlease();	
		}

		@Override
		public void loginFailed()
		{
			mpimAuth.setState(ConnectionState.NONAUTHENTICATED);
			mpimAuth.wakeMePlease();
		}
	}

	private class ContactListHandler implements ContactListCallbacks
	{
		private XMPPConnection connection;

		public ContactListHandler(XMPPConnection connection)
		{
			assert(connection != null);
			this.connection = connection;
		}

		@Override
		public void contactListReady()
		{
		}

		@Override
		public void contactChangedStatus(LegacyContact contact)
		{
			System.out.println("(II) Contact '"+ contact.displayName + "' changed his/her status to " + contact.status);
			try {
				connection.write(MessageBuilder.bluildContactPresenceStanza(contact));
			} catch (IOException e) {
			}
		}
	}

	private class MessageHandler implements MessageCallbacks
	{
		private XMPPConnection connection;

		public MessageHandler(XMPPConnection connection)
		{
			assert(connection != null);
			this.connection = connection;
		}

		@Override
		public void receivedMessage(String from, String to, String message)
		{
			try {
				connection.write(new MessageChatStanza(from, to, StringEscapeUtils.escapeHtml(message)));
			} catch (IOException e) {
			}
		}
	}
}
