package com.bluekitchen.btstack;

import com.bluekitchen.btstack.event.BTstackEventDaemonDisconnect;

public class BTstackClient {

	/**
	 * BTstack Server Client
	 * uses background receive thread
	 */
	
	public static final int DEFAULT_TCP_PORT = 13333;
	public static final String DEFAULT_UNIX_SOCKET = "/tmp/BTstack";

	private volatile SocketConnection socketConnection;
	private PacketHandler packetHandler;
	private boolean connected;
	private Thread rxThread;
	private String unixDomainSocketPath;
	private int tcpPort;
	
	public BTstackClient(){
		connected = false;
		socketConnection = null;
		rxThread = null;
	}

	public void setUnixDomainSocketPath(String path){
		this.unixDomainSocketPath = path;
	}

	public void setTcpPort(int port){
		this.tcpPort = port;
	}
	
	public void registerPacketHandler(PacketHandler packetHandler){
		this.packetHandler = packetHandler;
	}
	
	public boolean connect(){
		
		if (tcpPort == 0){
			try {
				Class<?> clazz = Class.forName("com.bluekitchen.btstack.SocketConnectionUnix");
				socketConnection = (SocketConnection) clazz.newInstance();
				if (unixDomainSocketPath != null){
					socketConnection.setUnixDomainSocketPath(unixDomainSocketPath);
				}
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
				return false;
			}
			
		} else {
			// TODO implement SocketConnectionTcp
			socketConnection = new SocketConnectionTCP();
			socketConnection.setTcpPort(tcpPort);
		}
		
		connected = socketConnection.connect();
		if (!connected) return false;
		
		rxThread = new Thread(new Runnable(){
			@Override
			public void run() {
				while (socketConnection != null && !Thread.currentThread().isInterrupted()){
					Packet packet = socketConnection.receivePacket();
					if (packet == null) {
						// server disconnected
						System.out.println("Rx Thread: Daemon Disconnected");
						packetHandler.handlePacket(new BTstackEventDaemonDisconnect());
						return;
					}
					if (packet.getPacketType() == Packet.HCI_EVENT_PACKET){
						packetHandler.handlePacket(EventFactory.eventForPacket(packet));
						continue;
					}
					packetHandler.handlePacket(packet);
				}
				System.out.println("Rx Thread: Interrupted");
			}
		});
		rxThread.start();
		
		return true;
	}
	
	public boolean sendPacket(Packet packet){
		if (socketConnection == null) return false;
		return socketConnection.sendPacket(packet);
	}

	public void disconnect(){
		if (socketConnection == null) return;
		if (rxThread == null) return;
		rxThread.interrupt();
		socketConnection.disconnect();
		socketConnection = null;
	}
}