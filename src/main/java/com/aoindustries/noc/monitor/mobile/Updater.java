/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2009-2012, 2020, 2021  AO Industries, Inc.
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

import com.tinyline.util.GZIPInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Date;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.SecureConnection;

/**
 * Asynchronously updates the node tree in a background Thread.  Each update
 * request will time-out after five minutes.  After each successful load,
 * it stores the results as a record.
 *
 * @author  AO Industries, Inc.
 */
class Updater implements Runnable {

	/**
	 * Enabled/disabled debugging output.
	 */
	private static final boolean DEBUG = false;

	/**
	 * The server that will be contacted for the updates.
	 */
	private static final String HOST = "monitor.aoindustries.com";

	/**
	 * The port that will be contacted for the updates.
	 */
	private static final int PORT = 4585;

	/**
	 * The number of milliseconds between updates.
	 */
	private static final long UPDATE_INTERVAL = 5L * 60 * 1000;

	/**
	 * The time-out duration.
	 */
	private static final long TIMEOUT_DURATION = 5L * 60 * 1000;

	private final String username;
	private final String password;

	private Thread thread;

	private transient boolean updateNow;
	private transient NodeSnapshot snapshot;

	// Could possibly change this to a single value if no more than one is ever added
	private final Vector listeners = new Vector();

	public Updater(String username, String password) {
		this.username = username;
		this.password = password;
	}

	void start() {
		try {
			synchronized(this) {
				if(thread==null) {
					thread = new Thread(this);
					thread.setPriority(Thread.NORM_PRIORITY-1);
					thread.start();
				}
			}
		} catch(Exception err) {
			alert(err);
		}
	}

	void stop() {
		synchronized(this) {
			thread = null;
		}
	}

	/**
	 * Causes an update of the data as soon as possible.  If an update is in progress,
	 * it will not cause another update after completion.
	 */
	void updateNow() {
		try {
			updateNow = true;
			synchronized(this) {
				if(thread!=null) thread.interrupt(); // TODO: Use wait/notify instead of interrupt
			}
		} catch(Exception err) {
			alert(err);
		}
	}

	public void run() {
		try {
			final Thread currentThread = Thread.currentThread();
			while(true) {
				long lastStartTime = System.currentTimeMillis();
				try {
					// Stop if should no longer be running
					Thread runningThread;
					synchronized(this) {
						runningThread = this.thread;
					}
					if(currentThread!=runningThread) break;

					// Try to retrieve latest values from a record if not yet loaded
					if(snapshot==null) {
						snapshot = NodeSnapshot.getRecord();
						if(snapshot!=null) {
							if(DEBUG) {
								System.out.println("Got old version from record store");
								dumpSnapshot(snapshot);
							}
							notifyListenersNodesUpdated(snapshot);
						}
					}

					// Download the latest values from the noc-monitor-server
					snapshot = downloadSnapshot();
					if(DEBUG) {
						System.out.println("Got new version from server");
						dumpSnapshot(snapshot);
					}

					// Store the latest values as a new version of the record
					NodeSnapshot.storeRecord(snapshot);
					notifyListenersNodesUpdated(snapshot);

					// Wait five minutes or until interrupted
					final long sleepUntil = lastStartTime + UPDATE_INTERVAL;
					while(!updateNow) {
						long sleepLeft = sleepUntil - System.currentTimeMillis();
						// Sleep done or not needed
						if(sleepLeft<=0) break;
						// System time reset
						if(sleepLeft>UPDATE_INTERVAL) break;
						try {
							if(DEBUG) System.out.println("Updater: Sleeping for "+sleepLeft+" ms");
							Thread.sleep(sleepLeft);
						} catch(InterruptedException err) {
							if(updateNow) break;
							if(DEBUG) System.out.println("Updater: Got interrupt that was not for updateNow flag");
						}
					}
				} catch(Exception err) {
					alert(err);
					try {
						Thread.sleep(60L * 1000);
					} catch(InterruptedException err2) {
						if(!updateNow) {
							if(DEBUG) System.out.println("Updater: Got interrupt that was not for updateNow flag");
						}
					}
				}
			}
		} catch(Exception err) {
			alert(err);
		}
	}

	private static void dumpSnapshot(NodeSnapshot snapshot) {
		System.out.print("Snapshot at ");
		System.out.println(new Date(snapshot.getTime()));
		dumpTree(0, snapshot.getRootNode());
	}

	private static void dumpTree(int indent, Node node) {
		for(int c=0;c<indent;c++) {
			System.out.print("    ");
		}
		System.out.print(node.getLabel());
		System.out.print(' ');
		System.out.print(AlertLevel.getDisplay(node.getAlertLevel()));
		String alertMessage = node.getAlertMessage();
		if(alertMessage!=null) {
			System.out.print(" \"");
			System.out.print(alertMessage);
			System.out.print('"');
		}
		System.out.println();
		Vector children = node.getChildren();
		if(children!=null) {
			for(int c=0, len=children.size(); c<len; c++) dumpTree(indent+1, (Node)children.elementAt(c));
		}
	}

	/**
	 * Gets the current snapshot or <code>null</code> if not yet available.
	 */
	NodeSnapshot getNodeSnapshot() {
		return snapshot;
	}

	/**
	 * Downloads a snapshot of the current values in a background Thread.
	 * Will time-out at five minutes.
	 */
	private NodeSnapshot downloadSnapshot() throws IOException {
		try {
			final long time = System.currentTimeMillis();
			final NodeSnapshot[] result = new NodeSnapshot[1];
			final IOException[] ioException = new IOException[1];
			final Thread outerThread = Thread.currentThread();
			Thread loaderThread = new Thread(
				new Runnable() {
					public void run() {
						try {
							NodeSnapshot newSnapshot;
							// Load the full tree
							SecureConnection conn = (SecureConnection)Connector.open("ssl://"+HOST+":"+PORT, Connector.READ_WRITE, true);
							try {
								DataOutputStream out = conn.openDataOutputStream();
								try {
									out.writeUTF(username);
									out.writeUTF(password);
									out.flush();
									DataInputStream in = new DataInputStream(new GZIPInputStream(conn.openInputStream()));
									try {
										if(!in.readBoolean()) {
											// Login unsuccessful
											throw new IOException("Login failed");
										}
										newSnapshot = new NodeSnapshot(NodeSnapshot.readNodeTree(in, null), time);
									} finally {
										in.close();
									}
								} finally {
									out.close();
								}
							} finally {
								conn.close();
							}
							// Set the result
							synchronized(result) {
								result[0] = newSnapshot;
							}
							// Interrupt the thread that is waiting for the result
							outerThread.interrupt(); // TODO: Use wait/notify instead of interrupt
						} catch(IOException err) {
							synchronized(ioException) {
								ioException[0] = err;
								outerThread.interrupt(); // TODO: Use wait/notify instead of interrupt
							}
						}
					}
				}
			);
			loaderThread.start();
			long startTime = System.currentTimeMillis();
			while(true) {
				synchronized(result) {
					NodeSnapshot res = result[0];
					if(res!=null) return res;
				}
				synchronized(ioException) {
					IOException err = ioException[0];
					if(err!=null) throw err;
				}
				long timeLeft = startTime + TIMEOUT_DURATION - System.currentTimeMillis();
				if(timeLeft<=0) throw new InterruptedIOException("Time-out retrieving systems node tree");
				try {
					Thread.sleep(timeLeft);
				} catch(InterruptedException err) {
					// This is expected
				}
			}
		} finally {
			updateNow = false;
		}
	}

	void addUpdaterListener(UpdaterListener listener) {
		synchronized(listeners) {
			listeners.addElement(listener);
		}
	}

	void removeUpdaterListener(UpdaterListener listener) {
		synchronized(listeners) {
			for(int c=listeners.size()-1;c>=0;c--) {
				if(listeners.elementAt(c)==listener) listeners.removeElementAt(c);
			}
		}
	}

	private void notifyListenersNodesUpdated(NodeSnapshot snapshot) {
		synchronized(listeners) {
			for(int c=0, len=listeners.size(); c<len; c++) {
				try {
					((UpdaterListener)listeners.elementAt(c)).nodesUpdated(snapshot);
				} catch(Exception err) {
					alert(err);
				}
			}
		}
	}

	private void alert(Exception err) {
		synchronized(listeners) {
			for(int c=0, len=listeners.size(); c<len; c++) {
				try {
					((UpdaterListener)listeners.elementAt(c)).alert(err);
				} catch(Exception err2) {
					err2.printStackTrace();
				}
			}
		}
	}
}
