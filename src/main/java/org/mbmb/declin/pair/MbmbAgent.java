package org.mbmb.declin.pair;

import java.nio.charset.Charset;
import java.util.Objects;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class MbmbAgent implements Runnable {
	@Override
	public void run() {
		try (ZContext ctx = new ZContext()) {
			ZMQ.Socket agentServer = ctx.createSocket(ZMQ.ROUTER);
			agentServer.bind("tcp://*:5570");
			ZMQ.Socket sendChannel = ctx.createSocket(ZMQ.PAIR);
			sendChannel.bind("inproc://agentsender");
			//
			ZMQ.Poller poller = ctx.createPoller(2);
			poller.register(agentServer, ZMQ.Poller.POLLIN);
			poller.register(sendChannel, ZMQ.Poller.POLLIN);
			String recvdIdentity = null;
			boolean sending = false;
			while (!Thread.currentThread().isInterrupted()) {
				poller.poll();
				// check the incoming from the agent
				if (poller.pollin(0)) {
					ZMsg msg = ZMsg.recvMsg(agentServer);
					ZFrame address = msg.pop();
					ZFrame content = Objects.requireNonNull(msg.pop());
					recvdIdentity = address.getString(Charset.defaultCharset());
					System.out.print("MBMB agent received: ");
					content.print("agent: ");
					msg.destroy();
					address.send(agentServer, ZFrame.REUSE + ZFrame.MORE);
					content.send(agentServer, ZFrame.REUSE);
					address.destroy();
					content.destroy();
				}
				// check the incoming from the sending channel
				if (recvdIdentity != null && poller.pollin(1)) {
					ZMsg msg = ZMsg.recvMsg(sendChannel);
					System.out.print("MBMB agent to send: ");
					msg.getLast().print("agent");
					ZFrame recipient = new ZFrame(recvdIdentity);
					ZFrame echo = new ZFrame(msg.getLast().getData());
					recipient.send(agentServer, ZFrame.REUSE + ZFrame.MORE);
					echo.send(agentServer, ZFrame.REUSE);
					recipient.destroy();
					echo.destroy();
					msg.destroy();
				}
				// if we got a message from a process, start sending updates
				if (recvdIdentity != null && !sending) {
					new Thread(new Sender(ctx, recvdIdentity)).start();
					sending = true;
				}
			}
		}
	}

	private class Sender implements Runnable {
		private ZContext ctx;
		private String recvdIdentity;

		Sender(final ZContext ctx, final String recvdIdentity) {
			this.ctx = ctx;
			this.recvdIdentity = recvdIdentity;
		}

		@Override
		public void run() {
			try {
				ZMQ.Socket sendChannel = this.ctx.createSocket(ZMQ.PAIR);
				sendChannel.connect("inproc://agentsender");

				int sent = 0;
				while (!Thread.currentThread().isInterrupted()) {
					ZFrame recipient = new ZFrame(recvdIdentity);
					ZFrame statusUpdate = new ZFrame("yyyyyooooooo[" + (sent++) + "]");
					recipient.send(sendChannel, ZFrame.REUSE + ZFrame.MORE);
					statusUpdate.send(sendChannel, ZFrame.REUSE);
					recipient.destroy();
					statusUpdate.destroy();
					Thread.sleep(250);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
