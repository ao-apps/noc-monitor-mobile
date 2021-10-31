/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2009, 2020  AO Industries, Inc.
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

/**
 * Notified of update events.
 *
 * @see  Updater
 * @see  Systems
 * 
 * @author  AO Industries, Inc.
 */
public interface UpdaterListener {

	/**
	 * Called when new nodes become available.
	 */
	void nodesUpdated(NodeSnapshot snapshot);

	/**
	 * Called when an exception occurs.
	 */
	void alert(Exception err);
}
