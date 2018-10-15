package org.mbmb.declin.pair;

public class Driver {

	public static void main(String[] args) {
		new Thread(new MbmbAgent()).start();
		new Thread(new MbmbClient()).start();
	}
}
