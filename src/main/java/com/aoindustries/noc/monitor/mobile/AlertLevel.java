/*
 * noc-monitor-mobile - Java ME Interface for Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2020  AO Industries, Inc.
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
 * along with noc-monitor-mobile.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.mobile;

/**
 * Constants used as alert levels.
 * 
 * @author  AO Industries, Inc.
 */
class AlertLevel {

	static final byte
		NONE = 0,
		LOW = 1,
		MEDIUM = 2,
		HIGH = 3,
		CRITICAL = 4,
		UNKNOWN = 5
	;

	private AlertLevel() {}

	static String getDisplay(byte alertLevel) {
		switch(alertLevel) {
			case NONE     : return "None";
			case LOW      : return "Low";
			case MEDIUM   : return "Medium";
			case HIGH     : return "High";
			case CRITICAL : return "Critical";
			case UNKNOWN  : return "Unknown";
			default       : throw new RuntimeException("Unexpected value for alertLevel: "+alertLevel);
		}
	}
}
