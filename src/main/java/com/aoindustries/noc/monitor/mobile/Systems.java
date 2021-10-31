/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2009, 2020, 2021  AO Industries, Inc.
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

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;

/**
 * Once every 5 minutes, downloads a snapshot of the entire systems state tree.
 * This includes only the node hierarchy, including names, alert levels, and alert messages.
 * This does not include the node details.  Node details are retrieved on demand.
 *
 * All retrievals will be handled by a single background thread.  However, if that
 * thread times-out it will be stopped and a new thread will be created in its place.
 *
 * It will also display a status message of "retrieving" when downloading.  Has an
 * option to "Retrieve now".
 *
 * Possible improvements:
 *     Use record to remember last alert level filter
 *     Report warnings on all else conditions or unexpected conditions/states
 *
 * TODO: buzz when there is a new critical alert, remove alert if it does away
 * TODO: how to detect if on the phone?
 *
 * @author  AO Industries, Inc.
 */
public class Systems extends MIDlet implements UpdaterListener, ItemStateListener, ItemCommandListener {

	private static final boolean DEBUG = false;

	private static final long TIME_FIELD_UPDATE_INTERVAL = 30000L;

	private Updater updater;
	private final Object updateLock = new Object();

	//private Form errorScreen;

	private transient boolean isPaused;
	private Form form;

	private final Object updatedTimeFieldLock = new Object();
	private StringItem updatedTimeField;
	private Thread updatedTimeFieldThread;

	private ChoiceGroup filter;
	private ChoiceGroup tree;
	private Vector treePaths = new Vector();
	private StringItem alertMessage;
	private byte alertLevel = AlertLevel.LOW;
	private Vector path = new Vector();

	public Systems() {
		// TODO: No longer hard-code, prompt at start-up
		updater = new Updater(Authentication.USERNAME, Authentication.PASSWORD);
	}

	protected void startApp() {
		try {
			isPaused = false;
			if(form==null) {
				Form newForm = new Form("Systems");

				// Items
				newForm.setItemStateListener(this);

				// Filter
				filter = new ChoiceGroup("Filter", Choice.POPUP);
				filter.append("All", getDotAlertImage(AlertLevel.NONE));
				filter.append("Low", getDotAlertImage(AlertLevel.LOW));
				filter.append("Medium", getDotAlertImage(AlertLevel.MEDIUM));
				filter.append("High", getDotAlertImage(AlertLevel.HIGH));
				filter.append("Critical", getDotAlertImage(AlertLevel.CRITICAL));
				filter.setSelectedIndex(alertLevel, true);
				filter.setLayout(Item.LAYOUT_LEFT|Item.LAYOUT_SHRINK);
				newForm.append(filter);

				updatedTimeField = new StringItem("Last Updated", "");
				//updatedDateField.setLayout(Item.LAYOUT_NEWLINE_AFTER);
				//updatedDateField.setLayout(Item.LAYOUT_2);
				updatedTimeField.addCommand(new Command("Update Now", Command.SCREEN, 2));
				updatedTimeField.setItemCommandListener(this);
				newForm.append(updatedTimeField);

				tree = new ChoiceGroup(null, Choice.EXCLUSIVE);
				tree.setFitPolicy(Choice.TEXT_WRAP_OFF);

				alertMessage = new StringItem(null, "");

				Display display = Display.getDisplay(this);
				display.setCurrent(newForm);
				form = newForm;
			}
			updater.start();
			updater.addUpdaterListener(this);
			updateForm(updater.getNodeSnapshot());
			synchronized(updatedTimeFieldLock) {
				if(updatedTimeFieldThread==null) {
					updatedTimeFieldThread = new Thread(
						new Runnable() {
							public void run() {
								Thread currentThread = Thread.currentThread();
								while(!currentThread.isInterrupted()) {
									try {
										synchronized(updatedTimeFieldLock) {
											if(currentThread!=updatedTimeFieldThread) break;
										}
										updateTimeField(updater.getNodeSnapshot());
									} catch(Exception err) {
										alert(err);
									}
									try {
										Thread.sleep(TIME_FIELD_UPDATE_INTERVAL);
									} catch(InterruptedException err) {
										// Restore the interrupted status
										Thread.currentThread().interrupt();
									} catch(Exception err) {
										alert(err);
									}
								}
							}
						}
					);
					updatedTimeFieldThread.setPriority(Thread.NORM_PRIORITY-2);
					updatedTimeFieldThread.start();
				}
			}
		} catch(Exception err) {
			alert(err);
		}
	}

	protected void pauseApp() {
		try {
			isPaused = true;
			synchronized(updatedTimeFieldLock) {
				updatedTimeFieldThread = null;
			}
			updater.removeUpdaterListener(this);
			// The updater will continue to run even when app is paused
		} catch(Exception err) {
			alert(err);
		}
	}

	protected void destroyApp(boolean unconditional) {
		try {
			updater.stop();
		} catch(Exception err) {
			alert(err);
		}
	}

	public void nodesUpdated(NodeSnapshot snapshot) {
		try {
			updateForm(snapshot);
		} catch(Exception err) {
			alert(err);
		}
	}

	private static final Date formatTimeDate = new Date(0);
	private static final Calendar formatTimeCal = Calendar.getInstance();

	/**
	 * Formats a date as used by the application.
	 */
	private static void formatTime(long date, StringBuffer formatTimeSB) {
		synchronized(formatTimeDate) {
			formatTimeDate.setTime(date);
			formatTimeCal.setTime(formatTimeDate);
			int hour = formatTimeCal.get(Calendar.HOUR);
			formatTimeSB.append(hour).append(':');
			int minute = formatTimeCal.get(Calendar.MINUTE);
			if(minute<10) formatTimeSB.append('0');
			formatTimeSB.append(minute);
			int amPm = formatTimeCal.get(Calendar.AM_PM);
			if(amPm==Calendar.AM) formatTimeSB.append(" AM");
			else if(amPm==Calendar.PM) formatTimeSB.append(" PM");
			else formatTimeSB.append(" ??");
		}
	}

	private void updateForm(NodeSnapshot snapshot) throws IOException {
		synchronized(updateLock) {
			updateTimeField(snapshot);

			int formIndex = 2;

			if(snapshot==null) {
				// Remove all form items after the date field
				path.removeAllElements();
				tree.deleteAll();
				treePaths.removeAllElements();
				while(form.size()>formIndex) form.delete(form.size()-1);
			} else {
				int treeIndex = 0;
				// Make sure the path is still valid, keep as much as possible
				Vector currentChildren = new Vector(1);
				Node currentNode = null;
				String[] currentNodePath = null;
				currentChildren.addElement(snapshot.getRootNode());
				for(int c=0; c<path.size(); c++) {
					if(DEBUG) System.out.println("currentChildren.size()="+currentChildren.size());
					String pathLabel = (String)path.elementAt(c);
					// Look for matching child that is high enough alert level
					Node foundChild = null;
					for(int d=0, len2=currentChildren.size(); d<len2; d++) {
						Node child = (Node)currentChildren.elementAt(d);
						if(child.getLabel().equals(pathLabel)) {
							foundChild = child;
							break;
						}
					}
					if(foundChild!=null) {
						// Find the new children
						Vector newChildren = foundChild.getChildren();
						currentChildren.removeAllElements();
						if(newChildren!=null) {
							int newLen = newChildren.size();
							if(DEBUG) System.out.println("newChildren.size()="+newChildren.size());
							currentChildren.ensureCapacity(newLen);
							for(int d=0; d<newLen; d++) {
								Node newChild = (Node)newChildren.elementAt(d);
								if(DEBUG) System.out.println("newChild="+newChild.getLabel()+" alertLevel="+newChild.getAlertLevel());
								if(newChild.getAlertLevel()>=alertLevel) currentChildren.addElement(newChild);
							}
						}
						// Build the new path
						String[] newPath = new String[treeIndex+1];
						for(int d=0; d<=treeIndex; d++) newPath[d] = (String)path.elementAt(d);
						// Add to the tree
						String label = foundChild.getLabel();
						Image alertImage = getAlertImage(
							foundChild.getAlertLevel(),
							foundChild.getAllowsChildren(),
							currentChildren.size()>1
						);
						currentNode = foundChild;
						currentNodePath = newPath;
						if(tree.size()<=treeIndex) {
							tree.append(label, alertImage);
							treePaths.addElement(newPath);
						} else {
							if(!tree.getString(treeIndex).equals(label) || alertImage!=tree.getImage(treeIndex)) tree.set(treeIndex, label, alertImage);
							treePaths.setElementAt(newPath, treeIndex);
						}
						treeIndex++;
					} else {
						// Delete rest of path
						while(path.size()>c) {
							if(DEBUG) System.out.println("Removing not longer found path element of "+path.elementAt(path.size()-1));
							path.removeElementAt(path.size()-1);
						}
						break;
					}
				}

				// Auto-select the first path element
				if(path.isEmpty() && currentChildren.size()==1) {
					currentNode = (Node)currentChildren.elementAt(0);
					// Find the new children
					Vector newChildren = currentNode.getChildren();
					currentChildren.removeAllElements();
					if(newChildren!=null) {
						int newLen = newChildren.size();
						if(DEBUG) System.out.println("newChildren.size()="+newChildren.size());
						currentChildren.ensureCapacity(newLen);
						for(int d=0; d<newLen; d++) {
							Node newChild = (Node)newChildren.elementAt(d);
							if(DEBUG) System.out.println("newChild="+newChild.getLabel()+" alertLevel="+newChild.getAlertLevel());
							if(newChild.getAlertLevel()>=alertLevel) currentChildren.addElement(newChild);
						}
					}
					// Build the new path
					currentNodePath = new String[] {currentNode.getLabel()};
					// Add to the tree
					String label = currentNode.getLabel();
					Image alertImage = getAlertImage(
						currentNode.getAlertLevel(),
						currentNode.getAllowsChildren(),
						currentChildren.size()>1
					);
					if(tree.size()<=treeIndex) {
						tree.append(label, alertImage);
						treePaths.addElement(currentNodePath);
					} else {
						if(!tree.getString(treeIndex).equals(label) || alertImage!=tree.getImage(treeIndex)) tree.set(treeIndex, label, alertImage);
						treePaths.setElementAt(currentNodePath, treeIndex);
					}
					treeIndex++;
				}

				// Children
				if(DEBUG) System.out.println("currentChildren.size()="+currentChildren.size());
				int len = currentChildren.size();
				// Temporarily stores children's children
				Vector currentNewChildren = new Vector();
				for(int c=0; c<len; c++) {
					Node child = (Node)currentChildren.elementAt(c);
					// Find the new children
					Vector newChildren = child.getChildren();
					currentNewChildren.removeAllElements();
					if(newChildren!=null) {
						int newLen = newChildren.size();
						if(DEBUG) System.out.println("newChildren.size()="+newChildren.size());
						currentNewChildren.ensureCapacity(newLen);
						for(int d=0; d<newLen; d++) {
							Node newChild = (Node)newChildren.elementAt(d);
							if(DEBUG) System.out.println("newChild="+newChild.getLabel()+" alertLevel="+newChild.getAlertLevel());
							if(newChild.getAlertLevel()>=alertLevel) currentNewChildren.addElement(newChild);
						}
					}
					// Build the new path
					String label = child.getLabel();
					String[] newPath;
					if(currentNodePath==null) {
						newPath = new String[1];
						newPath[0] = label;
					} else {
						int cnpLen = currentNodePath.length;
						newPath = new String[cnpLen+1];
						System.arraycopy(currentNodePath, 0, newPath, 0, cnpLen);
						newPath[cnpLen] = label;
					}
					// Add to the tree
					Image alertImage = getAlertImage(
						child.getAlertLevel(),
						child.getAllowsChildren(),
						!currentNewChildren.isEmpty()
					);
					if(tree.size()<=treeIndex) {
						if(DEBUG) System.out.println("Adding child: "+label);
						tree.append(label, alertImage);
						treePaths.addElement(newPath);
					} else {
						if(!tree.getString(treeIndex).equals(label) || alertImage!=tree.getImage(treeIndex)) tree.set(treeIndex, label, alertImage);
						treePaths.setElementAt(newPath, treeIndex);
					}
					treeIndex++;
				}
				while(tree.size()>treeIndex) {
					int removeIndex = tree.size()-1;
					if(DEBUG) System.out.println("Deleting extra tree: "+tree.getString(removeIndex));
					tree.delete(removeIndex);
					treePaths.removeElementAt(removeIndex);
				}
				if(treeIndex==0) {
					if(form.size()>formIndex && form.get(formIndex)==tree) form.delete(formIndex);
				} else {
					tree.setSelectedIndex(currentNodePath.length-1, true);
					if(form.size()<=formIndex || form.get(formIndex)!=tree) form.insert(formIndex, tree);
					formIndex++;
				}

				// Alert message
				String newAlertMessage = currentNode==null ? null : currentNode.getAlertMessage();
				if(newAlertMessage==null) {
					if(form.size()>formIndex && form.get(formIndex)==alertMessage) form.delete(formIndex);
				} else {
					alertMessage.setText(newAlertMessage);
					if(form.size()<=formIndex || form.get(formIndex)!=alertMessage) form.insert(formIndex, alertMessage);
					formIndex++;
				}

				// Could add node-type-specific details ?retrieved on demand?
			}
		}
	}

	private final StringBuffer updateTimeFieldSB = new StringBuffer();

	/**
	 * Updates the time field for the current time since.
	 */
	private void updateTimeField(NodeSnapshot snapshot) {
		synchronized(updatedTimeFieldLock) {
			if(snapshot==null) updatedTimeField.setText("");
			else {
				updateTimeFieldSB.setLength(0);
				long time = snapshot.getTime();
				formatTime(time, updateTimeFieldSB);
				long minutes = (System.currentTimeMillis()-time) / 60000L;
				if(minutes<0) minutes = 0;
				if(minutes==1) updateTimeFieldSB.append(" (1 minute ago)");
				else updateTimeFieldSB.append(" (").append(minutes).append(" minutes ago)");
				updatedTimeField.setText(updateTimeFieldSB.toString());
			}
		}
	}

	public void commandAction(Command c, Item item) {
		try {
			if(item==updatedTimeField) {
				if("Update Now".equals(c.getLabel())) updater.updateNow();
			}
		} catch(Exception err) {
			alert(err);
		}
	}

	private void setAlertLevel(byte alertLevel) throws IOException {
		synchronized(updateLock) {
			if(this.alertLevel!=alertLevel) {
				if(DEBUG) System.out.println("Setting alertLevel to "+AlertLevel.getDisplay(alertLevel));
				this.alertLevel = alertLevel;
				updateForm(updater.getNodeSnapshot());
			}
		}
	}

	public void itemStateChanged(Item item) {
		try {
			if(item==tree) {
				if(DEBUG) System.out.println("tree changed");
				String[] newPath = (String[])treePaths.elementAt(tree.getSelectedIndex());
				path.removeAllElements();
				for(int c=0, len=newPath.length; c<len; c++) path.addElement(newPath[c]);
				synchronized(updateLock) {
					updateForm(updater.getNodeSnapshot());
				}
			} else if(item==filter) {
				setAlertLevel((byte)filter.getSelectedIndex());
			} else {
				if(DEBUG) System.out.println("Unexpected item: "+item);
			}
		} catch(Exception err) {
			alert(err);
		}
	}

	/**
	 * Gets the filename part for the provided alert level.
	 */
	private static String getAlertLevelFilename(byte alertLevel) {
		switch(alertLevel) {
			case AlertLevel.NONE     : return "none";
			case AlertLevel.LOW      : return "low";
			case AlertLevel.MEDIUM   : return "medium";
			case AlertLevel.HIGH     : return "high";
			case AlertLevel.CRITICAL : return "critical";
			case AlertLevel.UNKNOWN  : return "unknown";
			default                  : throw new RuntimeException("Unexpected value for alertLevel: "+alertLevel);
		}
	}

	private final Image[] fileImages = new Image[AlertLevel.UNKNOWN+1];
	private final Image[] folderImages = new Image[AlertLevel.UNKNOWN+1];
	private final Image[] folderPlusImages = new Image[AlertLevel.UNKNOWN+1];

	/**
	 * Gets the alert image.
	 */
	private Image getAlertImage(byte alertLevel, boolean isFolder, boolean showPlus) throws IOException {
		synchronized(fileImages) {
			Image[] images;
			if(isFolder) {
				if(showPlus) images = folderPlusImages;
				else images = folderImages;
			} else {
				if(showPlus) throw new IOException("Can't be both !isFolder and showPlus");
				else images = fileImages;
			}
			Image image = images[alertLevel];
			if(image==null) {
				final String filename;
				if(isFolder) {
					if(showPlus) filename = "/com/aoindustries/noc/monitor/mobile/images/folder_"+getAlertLevelFilename(alertLevel)+"_plus.png";
					else filename = "/com/aoindustries/noc/monitor/mobile/images/folder_"+getAlertLevelFilename(alertLevel)+".png";
				} else {
					if(showPlus) throw new IOException("Can't be both !isFolder and showPlus");
					else filename = "/com/aoindustries/noc/monitor/mobile/images/file_"+getAlertLevelFilename(alertLevel)+".png";
				}
				image = Image.createImage(filename);
				images[alertLevel] = image;
			}
			return image;
		}
	}

	private final Image[] dotAlertImages = new Image[AlertLevel.UNKNOWN+1];
	private Image getDotAlertImage(byte alertLevel) throws IOException {
		synchronized(dotAlertImages) {
			Image dotImage = dotAlertImages[alertLevel];
			if(dotImage==null) {
				dotImage = Image.createImage("/com/aoindustries/noc/monitor/mobile/images/dot_"+getAlertLevelFilename(alertLevel)+".png");
				dotAlertImages[alertLevel] = dotImage;
			}
			return dotImage;
		}
	}

	public void alert(Exception err) {
		try {
			if(isPaused) resumeRequest();
			err.printStackTrace();
			Display display = Display.getDisplay(this);
			vibrate(display);
			Alert alert = new Alert("Exception", err.toString(), null, AlertType.ERROR);
			alert.setTimeout(Alert.FOREVER);
			display.setCurrent(alert, form);
		} catch(Exception err2) {
			err2.printStackTrace();
		}
	}

	/**
	 * Vibrates if it is not currently Dan's school hours.
	 */
	private void vibrate(Display display) {
		try {
			// Check schedule
			if(!isClasstime()) display.vibrate(250);
		} catch(Exception err) {
			// This is called by the alert method so alerting on exception could
			// cause infinite recursion
			err.printStackTrace();
		}
	}

	/**
	 * Determines if it is currently classtime.
	 */
	private static boolean isClasstime() {
		Calendar cal = Calendar.getInstance();
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int minute = cal.get(Calendar.MINUTE);
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		return
			// PH201, MTWF 10:10-11:00 +- 5 minutes
			(
				(
					dayOfWeek==Calendar.MONDAY
					|| dayOfWeek==Calendar.TUESDAY
					|| dayOfWeek==Calendar.WEDNESDAY
					|| dayOfWeek==Calendar.FRIDAY
				) && (
					   (hour==10 && minute>= 5)
					|| (hour==11 && minute<= 5)
				)
			)
			// PH201L, T 11:10-14:00 +- 5 minutes
			|| (
				(
					dayOfWeek==Calendar.TUESDAY
				) && (
					   (hour==11 && minute>= 5)
					||  hour==12
					||  hour==13
					|| (hour==14 && minute<= 5)
				)
			)
			// CSC333, MWF 11:15-12:05 +- 5 minutes
			|| (
				(
					dayOfWeek==Calendar.MONDAY
					|| dayOfWeek==Calendar.WEDNESDAY
					|| dayOfWeek==Calendar.FRIDAY
				) && (
					   (hour==11 && minute>=10)
					|| (hour==12 && minute<=10)
				)
			)
			// CIS439, MWF 12:20-13:10 +- 5 minutes
			|| (
				(
					dayOfWeek==Calendar.MONDAY
					|| dayOfWeek==Calendar.WEDNESDAY
					|| dayOfWeek==Calendar.FRIDAY
				) && (
					   (hour==12 && minute>=15)
					|| (hour==13 && minute<=15)
				)
			)
		;
	}
}
