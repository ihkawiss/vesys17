package ch.fhwn.vesys.websockets.server;

import java.io.IOException;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.server.Server;

public class BankServer {

	public static void main(String[] args) throws DeploymentException, IOException {
		
		// initialize server
		Server server = new Server("localhost", 8888, "/server", null, BankServerEndpoint.class);

		// start server
		server.start();
		
		// inform via console
		System.out.println("Bank Server started!");
	}

}
