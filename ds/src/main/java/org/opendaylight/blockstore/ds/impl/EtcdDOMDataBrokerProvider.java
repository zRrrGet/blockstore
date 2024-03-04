/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.blockstore.ds.impl;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.inject.Provider;

import org.hyperledger.fabric.client.Contract;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.SerializedDOMDataBroker;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStoreConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a {@link DataBroker} backed by etcd for use in production and tests.
 *
 * @author Michael Vorburger.ch
 */
@SuppressWarnings("all")
public class EtcdDOMDataBrokerProvider implements Provider<DOMDataBroker>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdDOMDataBrokerProvider.class);

    private final String name;
    private final EtcdDataStore configDS;
    private final EtcdDataStore operDS;
    private final DOMDataBroker domDataBroker;
    //private final Contract contract;

    /**
     * Constructor.
     *
     * @param etcdClient        connection to (cluster of) etcd server/s
     * @param nodeName          name used as prefix in logs; intended for in-process
     *                          clustering test cases, not production (where it can
     *                          be empty)
     * @param schemas           the DOMSchemaService
     */
    public EtcdDOMDataBrokerProvider(String nodeName, DOMSchemaService schemas, Contract c) throws Exception {
        // choice of suitable executors originally inspired from
        // org.opendaylight.mdsal.binding.dom.adapter.test.ConcurrentDataBrokerTestCustomizer
        this(nodeName, schemas,
                Executors.newListeningSingleThreadExecutor("EtcdDB-commitCoordinator", LOG),
                Executors.newListeningCachedThreadPool("EtcdDB-DTCLs", LOG), c);
    }

    public EtcdDOMDataBrokerProvider(String nodeName, DOMSchemaService schemaService,
            ListeningExecutorService commitCoordinatorExecutor, ListeningExecutorService dtclExecutor, Contract c)
            throws Exception {
        //this.contract = c;
        this.name = nodeName;

        configDS = createConfigurationDatastore(CONFIGURATION, dtclExecutor, schemaService, c);
        operDS = createConfigurationDatastore(OPERATIONAL, dtclExecutor, schemaService, c);
        Map<LogicalDatastoreType, DOMStore> datastores = ImmutableMap.of(CONFIGURATION, configDS, OPERATIONAL, operDS);
        domDataBroker = new SerializedDOMDataBroker(datastores, commitCoordinatorExecutor);
    }

    public void init() throws Exception {
    }

    @Override
    public void close() throws Exception {
        if (operDS != null) {
            operDS.close();
        }
        if (configDS != null) {
            configDS.close();
        }
    }

    @Override
    public DOMDataBroker get() {
        return getDOMDataBroker();
    }

    public DOMDataBroker getDOMDataBroker() {
        return domDataBroker;
    }

    private EtcdDataStore createConfigurationDatastore(LogicalDatastoreType type,
            ExecutorService dataTreeChangeListenerExecutor, DOMSchemaService schemaService, Contract c) {
        EtcdDataStore store = new EtcdDataStore(name, type, dataTreeChangeListenerExecutor,
                InMemoryDOMDataStoreConfigProperties.DEFAULT_MAX_DATA_CHANGE_LISTENER_QUEUE_SIZE, true, c);
        return store;
    }
}
