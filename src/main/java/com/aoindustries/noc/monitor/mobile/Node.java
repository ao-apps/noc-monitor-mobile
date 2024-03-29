/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2009, 2020, 2022  AO Industries, Inc.
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
    if (parent != null) {
      synchronized (parent) {
        if (parent.children == null) {
          parent.children = new Vector();
        } else if (ASSERT) {
          // Make sure label not already used by a sibling
          for (int c=0, len=parent.children.size(); c<len; c++) {
            Node sibling = (Node)parent.children.elementAt(c);
            if (sibling.label.equals(label)) {
              throw new RuntimeException("Assertion: label already used by sibling: "+label);
            }
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
