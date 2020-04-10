/*
 * Copyright 2008-2009, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
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
