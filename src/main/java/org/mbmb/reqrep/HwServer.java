package org.mbmb.reqrep;

import org.zeromq.ZMQ;

public class HwServer {

	public static void main(String[] args) throws Exception {
		try (ZMQ.Context context = ZMQ.context(1)) {
			//  Socket to talk to clients
			try (ZMQ.Socket responder = context.socket(ZMQ.REP)) {
				responder.bind("tcp://*:5555");

				while (!Thread.currentThread().isInterrupted()) {
					// Wait for next request from the client
					byte[] request = responder.recv(0);
					System.out.println("Received " + new String(request));

					// Do some 'work'
					Thread.sleep(500);

					// Send reply back to client
					String reply = "World";
					responder.send(reply.getBytes(), 0);
				}
			}
		}
	}
}
