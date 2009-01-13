/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mobile;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;
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
 * TODO: Use record to remember last alert level filter
 *
 * @author  AO Industries, Inc.
 */
public class Systems extends MIDlet implements UpdaterListener, ItemStateListener, ItemCommandListener {

    private static final boolean DEBUG = false;

    private Updater updater;
    private final Object updateLock = new Object();

    //private Form errorScreen;

    private Form form;
    private StringItem updatedDateField;
    private ChoiceGroup filter;
    private ChoiceGroup tree;
    private Vector treePaths = new Vector();
    private StringItem alertMessage;
    private byte alertLevel;
    private Vector path = new Vector();

    public Systems() {
        // TODO: No longer hard-coded, prompt at start-up
        updater = new Updater(Authentication.USERNAME, Authentication.PASSWORD);
    }

    protected void startApp() {
        updater.start();
        updater.addUpdaterListener(this);
        try {
            updateForm(updater.getNodeSnapshot());
        } catch(IOException err) {
            err.printStackTrace();
        }
    }

    protected void pauseApp() {
        updater.removeUpdaterListener(this);
        // The updater will continue to run even when app is paused
    }

    protected void destroyApp(boolean unconditional) {
        updater.stop();
    }
    
    public void nodesUpdated(NodeSnapshot snapshot) throws IOException {
        updateForm(snapshot);
    }

    private void updateForm(NodeSnapshot snapshot) throws IOException {
        synchronized(updateLock) {
            if(form==null) {
                Form newForm = new Form("Systems");
                
                // Items
                newForm.setItemStateListener(this);

                // Filter
                filter = new ChoiceGroup("Filter", Choice.POPUP);
                filter.append("All", getAlertImage(AlertLevel.NONE));
                filter.append("Low", getAlertImage(AlertLevel.LOW));
                filter.append("Medium", getAlertImage(AlertLevel.MEDIUM));
                filter.append("High", getAlertImage(AlertLevel.HIGH));
                filter.append("Critical", getAlertImage(AlertLevel.CRITICAL));
                //filter.setLayout(Item.LAYOUT_NEWLINE_AFTER);
                newForm.append(filter);

                updatedDateField = new StringItem("Last Updated", "");
                //updatedDateField.setLayout(Item.LAYOUT_NEWLINE_AFTER);
                //updatedDateField.setLayout(Item.LAYOUT_2);
                updatedDateField.addCommand(new Command("Update Now", Command.SCREEN, 2));
                updatedDateField.setItemCommandListener(this);
                newForm.append(updatedDateField);
                
                tree = new ChoiceGroup(null, Choice.EXCLUSIVE);
                tree.setFitPolicy(Choice.TEXT_WRAP_OFF);

                alertMessage = new StringItem("Alert Message", "");

                Display display = Display.getDisplay(this);
                display.setCurrent(newForm);
                form = newForm;
            }
            updatedDateField.setText(snapshot==null ? "" : new Date(snapshot.getTime()).toString());

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
                        String label = foundChild.getLabel();
                        Image alertImage = getAlertImage(foundChild.getAlertLevel());
                        String[] newPath = new String[treeIndex+1];
                        for(int d=0; d<=treeIndex; d++) newPath[d] = (String)path.elementAt(d);
                        // Add if necessary
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
                    String label = currentNode.getLabel();
                    Image alertImage = getAlertImage(currentNode.getAlertLevel());
                    currentNodePath = new String[] {currentNode.getLabel()};
                    if(tree.size()<=treeIndex) {
                        tree.append(label, alertImage);
                        treePaths.addElement(currentNodePath);
                    } else {
                        if(!tree.getString(treeIndex).equals(label) || alertImage!=tree.getImage(treeIndex)) tree.set(treeIndex, label, alertImage);
                        treePaths.setElementAt(currentNodePath, treeIndex);
                    }
                    treeIndex++;
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
                }

                // Children
                if(DEBUG) System.out.println("currentChildren.size()="+currentChildren.size());
                int len = currentChildren.size();
                for(int c=0; c<len; c++) {
                    Node child = (Node)currentChildren.elementAt(c);
                    String label = child.getLabel();
                    Image alertImage = getAlertImage(child.getAlertLevel());
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

                // TODO: Node-type-specific details
            }
        }
    }

    public void commandAction(Command c, Item item) {
        if(item==updatedDateField) {
            if("Update Now".equals(c.getLabel())) updater.updateNow();
            // TODO: Throw error on all else conditions and display all errors
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
        } catch(IOException err) {
            err.printStackTrace();
        }
    }
    
    final private Image[] alertImages = new Image[AlertLevel.UNKNOWN+1];

    /**
     * Gets the alert image.
     * 
     * @param  indent  0, 1, or 2
     */
    // TODO: private Image getAlertImage(int indent, byte alertLevel, boolean allowsChildren, int numChildren) throws IOException {
    private Image getAlertImage(byte alertLevel) throws IOException {
        synchronized(alertImages) {
            Image image = alertImages[alertLevel];
            if(image==null) {
                String filename;
                switch(alertLevel) {
                    case AlertLevel.NONE     : filename = "none.png";     break;
                    case AlertLevel.LOW      : filename = "low.png";      break;
                    case AlertLevel.MEDIUM   : filename = "medium.png";   break;
                    case AlertLevel.HIGH     : filename = "high.png";     break;
                    case AlertLevel.CRITICAL : filename = "critical.png"; break;
                    case AlertLevel.UNKNOWN  : filename = "unknown.png";  break;
                    default                  : throw new RuntimeException("Unexpected value for alertLevel: "+alertLevel);
                }
                image = Image.createImage("/com/aoindustries/noc/monitor/mobile/"+filename);
                alertImages[alertLevel] = image;
            }
            return image;
        }
    }
}
