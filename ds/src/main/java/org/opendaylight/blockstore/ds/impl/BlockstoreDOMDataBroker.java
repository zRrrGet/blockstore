/*
 * Copyright (c) 2016 ... and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.blockstore.ds.impl;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.ForwardingDOMDataBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
// do NOT @Service(classes = DOMDataBroker.class), because we need odl:type="default" so BP XML
public class BlockstoreDOMDataBroker extends ForwardingDOMDataBroker {

    private static final Logger LOG = LoggerFactory.getLogger(BlockstoreDOMDataBroker.class);

    @Inject
    public BlockstoreDOMDataBroker(@Reference DOMSchemaService schemaService) throws Exception {
        LOG.info("NORMAL DOM BROKER!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    @PreDestroy
    public void close() throws Exception {
        LOG.info("NORMAL DOM BROKER CLOSE!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    @Override
    protected DOMDataBroker delegate() {
        LOG.info("NORMAL DOM BROKER DELEGATE!!!!!!!!!!!!!!!!!!!!!!!!");
        return null;
    }
}
