/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2020, 2021  AO Industries, Inc.
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

import com.aoindustries.io.IoUtils;
import java.security.SecureRandom;
import java.util.Random;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

/**
 * @author  AO Industries, Inc.
 */
public class MoireCanvas extends GameCanvas implements Runnable {

	/**
	 * A fast pseudo-random number generator for non-cryptographic purposes.
	 */
	private static final Random fastRandom = new Random(IoUtils.bufferToLong(new SecureRandom().generateSeed(Long.BYTES)));

	private Thread thread;
	private int lastX = -1;

	private static final int COLOR_MODE = 0;
	private static final int RAINBOW_MODE = 1;
	private static final int BW_MODE = 2;
	private static final int LINES = 32;
	private final int color_mode=RAINBOW_MODE;
	private final int color_rate=16;
	private final int delay=50;
	private final int brightness=255;
	private final int[] x1=new int[LINES];
	private final int[] y1=new int[LINES];
	private final int[] x2=new int[LINES];
	private final int[] y2=new int[LINES];
	private int cred=255;
	private int cgreen=0;
	private int cblue=0;
	private int color_part=1;
	private final int[] line_colors=new int[LINES];
	private final int foreground=0xff0000;
	private final int background=0x000000;
	private final int minimum=4096;
	private final int range=24576;
	private final boolean going=false;
	private int dx1=1;
	private int dy1=1;
	private int dx2=-1;
	private int dy2=-1;
	private int sx1=32767;
	private int sy1=32767;
	private int sx2=32767;
	private int sy2=32767;

	public MoireCanvas() {
		super(true);
	for(int c=0;c<LINES;++c) {
		line_colors[c]=background;
		x1[c]=y1[c]=x2[c]=y2[c]=524288;
	}
	}

	public void start() {
		synchronized(this) {
			if(thread==null) {
				thread = new Thread(this);
				thread.start();
			}
		}
	}

	public void stop() {
		synchronized(this) {
			thread = null;
		}
	}

	public void run() {
		/*
		Graphics g = getGraphics();
		Thread thisThread = Thread.currentThread();
		int width = getWidth();
		int height = getHeight();
		while(thisThread==thread) {
			boolean flushAll = false;
			g.setColor(0x000000);
			if(lastX!=-1) {
				g.drawLine(lastX, 0, lastX, height-1);
			} else {
				g.fillRect(0, 0, width, height);
				flushAll = true;
			}
			g.setColor(0xff0000);
			int x = lastX + 1;
			if(x>width) {
				x=0;
				flushAll = true;
			}
			g.drawLine(x, 0, x, height-1);
			if(flushAll) flushGraphics();
			else flushGraphics(lastX, 0, 2, height);
			lastX = x;
			try {
				Thread.sleep(30);
			} catch(InterruptedException err) {
				err.printStackTrace();
			}
		}*/
		final Graphics g = getGraphics();
		final Thread thisThread = Thread.currentThread();
		final int width = getWidth();
		final int height = getHeight();
	int lastline=LINES-1;
	int c;
	boolean going=this.going;
		while(thisThread==thread) {
		long time=System.currentTimeMillis();
			// Clear background
			g.setColor(background);
			g.fillRect(0, 0, width, height);
			// Draw lines
			int start;
			int end;
			int add;
			if(going) {
				start=0;
				end=LINES;
				add=1;
			} else {
				start=LINES-1;
				end=add=-1;
			}
			for(c=start;c!=end;c+=add) {
				g.setColor(line_colors[c]);
				g.drawLine(
						   (int)(((long)x1[c])*width/1048576),
						   (int)(((long)y1[c])*height/1048576),
						   (int)(((long)x2[c])*width/1048576),
						   (int)(((long)y2[c])*height/1048576)
						   );
			}
			// Scroll up data
			int t;
			System.arraycopy(x1, 0, x1, 1, t=LINES-1);
			System.arraycopy(y1, 0, y1, 1, t);
			System.arraycopy(x2, 0, x2, 1, t);
			System.arraycopy(y2, 0, y2, 1, t);
			System.arraycopy(line_colors, 0, line_colors, 1, t);

			// Move points
			c=x1[0]+dx1*sx1;
			if(c<0||c>1048575) {
				dx1=-dx1;
				sx1=((fastRandom.nextInt()&0x7fffffff)%range)+minimum;
				x1[0]+=dx1*sx1;
			} else x1[0]=c;
			c=y1[0]+dy1*sy1;
			if(c<0||c>1048575) {
				dy1=-dy1;
				sy1=((fastRandom.nextInt()&0x7fffffff)%range)+minimum;
				y1[0]+=dy1*sy1;
			} else y1[0]=c;
			c=x2[0]+dx2*sx2;
			if(c<0||c>1048575) {
				dx2=-dx2;
				sx2=((fastRandom.nextInt()&0x7fffffff)%range)+minimum;
				x2[0]+=dx2*sx2;
			} else x2[0]=c;
			c=y2[0]+dy2*sy2;
			if(c<0||c>1048575) {
				dy2=-dy2;
				sy2=((fastRandom.nextInt()&0x7fffffff)%range)+minimum;
				y2[0]+=dy2*sy2;
			} else y2[0]=c;
			// Change the Color
			if(color_mode==COLOR_MODE) line_colors[0]=foreground;
			else if(color_mode==RAINBOW_MODE) {
				switch(color_part) {
				case 1:
					cgreen+=color_rate;
					if(cgreen>brightness) {
						cgreen=brightness;
						color_part=2;
					}
					break;
				case 2:
					cred-=color_rate;
					if(cred<0) {
						cred=0;
						color_part=3;
					}
					break;
				case 3:
					cblue+=color_rate;
					if(cblue>brightness) {
						cblue=brightness;
						color_part=4;
					}
					break;
				case 4:
					cgreen-=color_rate;
					if(cgreen<0) {
						cgreen=0;
						color_part=5;
					}
					break;
				case 5:
					cred+=color_rate;
					if(cred>brightness) {
						cred=brightness;
						color_part=6;
					}
					break;
				case 6:
					cblue-=color_rate;
					if(cblue<0) {
						cblue=0;
						color_part=1;
					}
				}
				line_colors[0]=(cred<<16) | (cgreen<<8) | cblue;
			} else if(color_mode==BW_MODE) {
				if(color_part==1) {
					cred-=color_rate;
					if(cred<0) {
						cred=0;
						color_part=2;
					}
				} else {
					cred+=color_rate;
					if(cred>brightness) {
						cred=brightness;
						color_part=1;
					}
				}
				line_colors[0]=(cred<<16) | (cred<<8) | cred;
			}
			flushGraphics();
			int adelay=delay-(int)(System.currentTimeMillis()-time);
			if(adelay<=0) Thread.yield();
			else {
				try {
					Thread.sleep(adelay);
				} catch(InterruptedException e) {}
			}
		}
	}
}
