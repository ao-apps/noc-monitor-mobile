/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.mobile;

import java.io.IOException;

/**
 * Notified of update events.
 *
 * @see  Updater
 * @see  Systems
 * 
 * @author  AO Industries, Inc.
 */
interface UpdaterListener {
    
    /**
     * Called when new nodes become available.
     */
    void nodesUpdated(NodeSnapshot snapshot) throws IOException;
}
