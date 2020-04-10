/*
 * Copyright 2009, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mobile;

import java.util.Vector;

/**
 * Represents one node in the systems tree.
 * 
 * @author  AO Industries, Inc.
 */
public class Node {

	private static final boolean ASSERT = true;

	private Node parent;
	private String label;
	private byte alertLevel;
	private String alertMessage;
	private boolean allowsChildren;
	private Vector children;

	/**
	 * Creates the new node.  If <code>parent</code> is non-null, this is added to the list
	 * of children of its parent.
	 * 
	 * @param parent        the parent <code>Node</code> or <code>null</code> for the top of the tree.
	 * @param label         the unique-per-parent label for this node.
	 * @param alertLevel    the current alertLevel.
	 * @param alertMessage  the current alertMessage or <code>null</code> for none.
	 */
	Node(Node parent, String label, byte alertLevel, String alertMessage, boolean allowsChildren) {
		this.parent = parent;
		this.label = label;
		this.alertLevel = alertLevel;
		this.alertMessage = alertMessage;
		this.allowsChildren = allowsChildren;
		if(parent!=null) {
			synchronized(parent) {
				if(parent.children==null) parent.children = new Vector();
				else if(ASSERT) {
					// Make sure label not already used by a sibling
					for(int c=0, len=parent.children.size(); c<len; c++) {
						Node sibling = (Node)parent.children.elementAt(c);
						if(sibling.label.equals(label)) throw new RuntimeException("Assertion: label already used by sibling: "+label);
					}
				}
				parent.children.addElement(this);
			}
		}
	}

	/**
	 * Gets the parent of this node or <code>null</code> if this is the root.
	 */
	Node getParent() {
		return parent;
	}

	/**
	 * Gets the unique-per-parent label for this node.
	 */
	String getLabel() {
		return label;
	}

	/**
	 * Gets the alert level for this node.
	 * 
	 * @see  AlertLevel
	 */
	byte getAlertLevel() {
		return alertLevel;
	}

	/**
	 * Gets the most recent alert message for this node or <code>null</code>
	 * for none.
	 */
	String getAlertMessage() {
		return alertMessage;
	}

	/**
	 * Indicates this node may have children.
	 */
	boolean getAllowsChildren() {
		return allowsChildren;
	}

	/**
	 * Gets the children of this node or <code>null</code> if has none.
	 */
	synchronized Vector getChildren() {
		return children;
	}
}
