package org.ara;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import net.sf.jml.MsnContact;
import net.sf.jml.MsnList;
import net.sf.jml.MsnMessenger;
import net.sf.jml.MsnSwitchboard;
import net.sf.jml.MsnUserStatus;
import net.sf.jml.event.MsnContactListAdapter;
import net.sf.jml.event.MsnMessageAdapter;
import net.sf.jml.event.MsnMessengerAdapter;
import net.sf.jml.impl.MsnMessengerFactory;
import net.sf.jml.message.MsnControlMessage;
import net.sf.jml.message.MsnDatacastMessage;
import net.sf.jml.message.MsnInstantMessage;

import org.ara.xmpp.ConnectionState;
import org.ara.xmpp.MessageBuilder;
import org.ara.xmpp.MpimAuthenticate;

public class MPIMMessenger extends Thread
{
	private String email;
	private String password;
	private MsnMessenger messenger;
	private MpimAuthenticate mpimAuth;
	private SocketChannel socket;
	
	public MPIMMessenger(String stremail, String strpwd, MpimAuthenticate ma, SocketChannel sc)
	{
		email = stremail;
		password = strpwd;
		messenger = MsnMessengerFactory.createMsnMessenger(email, password);
		mpimAuth = ma;
		socket = sc;
			
		initListeners(messenger);
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
	
	public void sendRoster(String id){
		String toSend = MessageBuilder.buildRoster(id,email, getContacts());
		try {
			socket.write(ByteBuffer.wrap(toSend.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		sendContactListPresence();
	}
	
	public void sendContactListPresence(){
		for( MsnContact cont : messenger.getContactList().getContactsInList(MsnList.AL)){
			if(cont.getStatus() == MsnUserStatus.OFFLINE)
				continue;
			String toSend = "<presence from=\""+cont.getEmail().getEmailAddress()+"\" to=\""+email+"\" />";
			try {
				socket.write(ByteBuffer.wrap(toSend.getBytes()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public MsnContact[] getContacts(){
		
		MsnContact[] c; 
		while(true){		
			c = messenger.getContactList().getContactsInList(MsnList.AL);
			if(c == null || c.length == 0){
				goToSleep();
			}else{
				break;
			}
		}
		
		return messenger.getContactList().getContactsInList(MsnList.AL);
	}

	public void start()
	{
		messenger.login();
	}

	private void initListeners(MsnMessenger messenger)
	{
		messenger.addMessengerListener(new MPIM_MsnMessengerAdapter());
		messenger.addContactListListener(new MPIM_MsnContactListAdapter());
		messenger.addMessageListener(new MPIM_MsnMessageAdapter());
	}
	
	private class MPIM_MsnMessengerAdapter extends MsnMessengerAdapter
	{
		public void loginCompleted(MsnMessenger messenger)
		{
			System.out.println(messenger.getOwner().getEmail() + " login");
			messenger.getOwner().setStatus(MsnUserStatus.ONLINE);
			mpimAuth.setState(ConnectionState.AUTHENTICATED);
			mpimAuth.wakeMePlease();
		}

		public void logout(MsnMessenger messenger)
		{
			System.out.println(messenger.getOwner().getEmail() + " logout");
			mpimAuth.setState(ConnectionState.DISCONNECTED);
			mpimAuth.wakeMePlease();
		}

		public void exceptionCaught(MsnMessenger messenger, Throwable throwable)
		{
			System.out.println("caught exception: " + throwable);
			throwable.printStackTrace();
			mpimAuth.setState(ConnectionState.DISCONNECTED);
			mpimAuth.wakeMePlease();
		}
	}
	
	class MPIM_MsnContactListAdapter extends MsnContactListAdapter
	{
		public void contactListInitCompleted(MsnMessenger messenger)
		{
			wakeMePlease();
		}

		public void contactStatusChanged(MsnMessenger messenger, MsnContact contact)
		{
			System.out.println(contact.getDisplayName() + ":" + contact.getStatus() + " from " + contact.getOldStatus());
		}
	}
	
	public class MPIM_MsnMessageAdapter extends MsnMessageAdapter
	{
		public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact contact)
		{
			//text message received
			switchboard.sendMessage(message);
			System.out.println(message.getContent()); //tirar a mensagem
		}

		public void controlMessageReceived(MsnSwitchboard switchboard, MsnControlMessage message, MsnContact contact)
		{
			//such as typing message and recording message
			switchboard.sendMessage(message);
			System.out.println(message);
		}

		public void datacastMessageReceived(MsnSwitchboard switchboard, MsnDatacastMessage message, MsnContact contact)
		{
			//such as Nudge
			switchboard.sendMessage(message);
			System.out.println(message);
		}
	}
}