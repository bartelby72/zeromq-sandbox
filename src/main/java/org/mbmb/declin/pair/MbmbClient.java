package org.mbmb.declin.pair;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class MbmbClient implements Runnable {

	@Override
	public void run() {
		try (ZContext ctx = new ZContext()) {
			ZMQ.Socket agentConnection = ctx.createSocket(ZMQ.DEALER);
			//
			ZMQ.Socket sendChannel = ctx.createSocket(ZMQ.PAIR);
			sendChannel.bind("inproc://clientsender");
			//  Set random identity to make tracing easier
			String identity = "ambackend";
			agentConnection.setIdentity(identity.getBytes(ZMQ.CHARSET));
			agentConnection.connect("tcp://127.0.0.1:5570");

			ZMQ.Poller poller = ctx.createPoller(2);
			poller.register(agentConnection, ZMQ.Poller.POLLIN);
			poller.register(sendChannel, ZMQ.Poller.POLLIN);
			// spin off the sender
			new Thread(new Sender(ctx)).start();
			//
			while (!Thread.currentThread().isInterrupted()) {
				poller.poll();
				// check the incoming from the agent
				if (poller.pollin(0)) {
					ZMsg msg = ZMsg.recvMsg(agentConnection);
					System.out.print("MBMB client received: ");
					msg.getLast().print(identity);
					msg.destroy();
				}
				// check the incoming from the sending channel
				if (poller.pollin(1)) {
					ZMsg msg = ZMsg.recvMsg(sendChannel);
					System.out.print("MBMB client to send: ");
					msg.getLast().print(identity);
					agentConnection.send(msg.getLast().getData(), 0);
					msg.destroy();
				}
			}
		}
	}

	private class Sender implements Runnable {
		private ZContext ctx;

		Sender(final ZContext ctx) {
			this.ctx = ctx;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(500);
				ZMQ.Socket sendChannel = this.ctx.createSocket(ZMQ.PAIR);
				sendChannel.connect("inproc://clientsender");

				int sent = 0;
				while (!Thread.currentThread().isInterrupted()) {
					sendChannel.send(String.format("MBMB please send #%d", sent++), 0);
					Thread.sleep(2000);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
