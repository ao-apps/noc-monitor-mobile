/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mobile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

/**
 * Contains a root node and timestamp.
 * 
 * @author  AO Industries, Inc.
 */
public class NodeSnapshot {

    private static final boolean DEBUG = false;
    
    private static final boolean USE_RECORD_STORE = false;

    private static final String RECORD_NAME = "NodeSnapshot.cache";

    /**
     * Each time the record store format is changed in an incompatible way,
     * this should be incremented.
     */
    private static final short RECORD_STORE_FORMAT_VERSION = 1;

    /**
     * Reads a node tree from the provided DataInputStream.
     */
    static Node readNodeTree(DataInputStream in, Node parent) throws IOException {
        Node node = new Node(
            parent,
            in.readUTF(),
            in.readByte(),
            in.readBoolean()?in.readUTF():null,
            in.readBoolean()
        );
        int numChildren = in.readShort();
        for(int c=0; c<numChildren; c++) readNodeTree(in, node);
        return node;
    }

    private static void writeNodeTree(DataOutputStream out, Node node) throws IOException {
        out.writeUTF(node.getLabel());
        out.writeByte(node.getAlertLevel());
        String alertMessage = node.getAlertMessage();
        if(alertMessage!=null) {
            out.writeBoolean(true);
            out.writeUTF(alertMessage);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(node.getAllowsChildren());
        Vector children = node.getChildren();
        int numChildren = children==null ? 0 : children.size();
        if(numChildren>Short.MAX_VALUE) throw new IOException("Too many children for current protocol: "+numChildren);
        out.writeShort(numChildren);
        for(int c=0;c<numChildren;c++) writeNodeTree(out, (Node)children.elementAt(c));
    }

    private static final Object recordLock = new Object();

    /**
     * Caches the last record stored or retrieved.
     */
    private static NodeSnapshot lastRecord;
    private static int lastRecordVersion = 0;

    /**
     * Stores the provided node tree to the record.
     */
    static void storeRecord(NodeSnapshot snapshot) throws IOException, RecordStoreException {
        synchronized(recordLock) {
            if(USE_RECORD_STORE) {
                // Convert to a byte[]
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                try {
                    DataOutputStream out = new DataOutputStream(bout);
                    try {
                        out.writeShort(RECORD_STORE_FORMAT_VERSION);
                        writeNodeTree(out, snapshot.rootNode);
                        out.writeLong(snapshot.time);
                    } finally {
                        out.close();
                    }
                } finally {
                    bout.close();
                }
                byte[] newBytes = bout.toByteArray();

                // Store to the record
                RecordStore recordStore = RecordStore.openRecordStore(RECORD_NAME, true, RecordStore.AUTHMODE_PRIVATE, true);
                try {
                    if(recordStore.getNumRecords()==0) {
                        if(DEBUG) System.out.println("Adding record "+RECORD_NAME+"#1 of "+newBytes.length+" bytes");
                        int id = recordStore.addRecord(newBytes, 0, newBytes.length);
                        if(id!=1) {
                            recordStore.deleteRecord(id);
                            throw new RecordStoreException("Unexpected first ID: "+id);
                        }
                    } else {
                        if(DEBUG) System.out.println("Setting record "+RECORD_NAME+"#1 of "+newBytes.length+" bytes");
                        recordStore.setRecord(1, newBytes, 0, newBytes.length);
                    }
                    lastRecord = snapshot;
                    lastRecordVersion = recordStore.getVersion();
                } finally {
                    recordStore.closeRecordStore();
                }
            } else {
                lastRecord = snapshot;
                lastRecordVersion++;
            }
        }
    }

    /**
     * Gets the most recent record or <code>null</code> if unavailable.
     */
    static NodeSnapshot getRecord() throws IOException, RecordStoreException {
        synchronized(recordLock) {
            if(USE_RECORD_STORE) {
                // Fetch from the record
                try {
                    RecordStore recordStore = RecordStore.openRecordStore(RECORD_NAME, false, RecordStore.AUTHMODE_PRIVATE, false);
                    try {
                        // Use cache when record store hasn't changed
                        int recordStoreVersion = recordStore.getVersion();
                        if(lastRecord!=null && lastRecordVersion==recordStoreVersion) return lastRecord;

                        if(recordStore.getNumRecords()==0) {
                            lastRecord = null;
                            lastRecordVersion = -1;
                            return null;
                        }
                        byte[] oldBytes = recordStore.getRecord(1);
                        if(DEBUG) System.out.println("Got previous record "+RECORD_NAME+"#1 of "+oldBytes.length+" bytes");
                        DataInputStream in = new DataInputStream(new ByteArrayInputStream(oldBytes));
                        try {
                            int recordStoreFormatVersion = in.readShort();
                            if(recordStoreFormatVersion==RECORD_STORE_FORMAT_VERSION) {
                                lastRecordVersion = recordStoreVersion;
                                lastRecord = new NodeSnapshot(readNodeTree(in, null), in.readLong());
                            } else {
                                lastRecord = null;
                                lastRecordVersion = -1;
                            }
                            return lastRecord;
                        } finally {
                            in.close();
                        }
                    } finally {
                        recordStore.closeRecordStore();
                    }
                } catch(RecordStoreNotFoundException err) {
                    lastRecord = null;
                    lastRecordVersion = -1;
                    return null;
                }
            } else {
                return lastRecord;
            }
        }
    }

    final private Node rootNode;
    final private long time;
    
    NodeSnapshot(Node rootNode, long time) {
        this.rootNode = rootNode;
        this.time = time;
    }
    
    Node getRootNode() {
        return rootNode;
    }
    
    /**
     * Gets the time this snapshot was retrieved.
     */
    long getTime() {
        return time;
    }
}
