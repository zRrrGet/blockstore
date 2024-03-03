/*
 * Copyright (c) 2016 ... and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.blockstore.ds.impl;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.sal.core.compat.LegacyDOMDataBrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BlockstoreLegacyDOMDataBroker extends LegacyDOMDataBrokerAdapter
        implements org.opendaylight.controller.md.sal.dom.api.DOMDataBroker {

    private static final Logger LOG = LoggerFactory.getLogger(BlockstoreLegacyDOMDataBroker.class);

    @Inject
    public BlockstoreLegacyDOMDataBroker(BlockstoreDOMDataBroker delegate) {
        super(delegate);
    }

}
