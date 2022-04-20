/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2020, 2022  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-mobile.
 *
 * noc-monitor-mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-mobile.  If not, see <https://www.gnu.org/licenses/>.
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
