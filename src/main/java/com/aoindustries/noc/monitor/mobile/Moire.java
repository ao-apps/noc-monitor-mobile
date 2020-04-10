/*
 * Copyright 2008-2009, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mobile;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/**
 * @author  AO Industries, Inc.
 */
public class Moire extends MIDlet {

	private MoireCanvas canvas;

	public Moire() {
		canvas = new MoireCanvas();
	}

	protected void startApp() {
		Display display = Display.getDisplay(this);
		canvas.start();
		display.setCurrent(canvas);
	}

	protected void pauseApp() {
		canvas.stop();
	}

	protected void destroyApp(boolean unconditional) {
		canvas.stop();
	}
}
