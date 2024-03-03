/*
 * Copyright © 2018 no and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.blockstore.cli.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.blockstore.cli.api.BlockstoreCliCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockstoreCliCommandsImpl implements BlockstoreCliCommands {

    private static final Logger LOG = LoggerFactory.getLogger(BlockstoreCliCommandsImpl.class);
    private final DataBroker dataBroker;

    public BlockstoreCliCommandsImpl(final DataBroker db) {
        this.dataBroker = db;
        LOG.info("BlockstoreCliCommandImpl initialized");
    }

    @Override
    public Object testCommand(Object testArgument) {
        return "This is a test implementation of test-command";
    }
}
