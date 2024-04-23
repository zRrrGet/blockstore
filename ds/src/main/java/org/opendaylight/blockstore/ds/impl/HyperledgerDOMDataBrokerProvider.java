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

@SuppressWarnings("all")
public class HyperledgerDOMDataBrokerProvider implements Provider<DOMDataBroker>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HyperledgerDOMDataBrokerProvider.class);

    private final String name;
    private final HyperledgerDataStore configDS;
    private final HyperledgerDataStore operDS;
    private final DOMDataBroker domDataBroker;

    public HyperledgerDOMDataBrokerProvider(String nodeName, DOMSchemaService schemas, Contract c) throws Exception {
        this(nodeName, schemas,
                Executors.newListeningSingleThreadExecutor("HyperledgerDB-commitCoordinator", LOG),
                Executors.newListeningCachedThreadPool("HyperledgerDB-DTCLs", LOG), c);
    }

    public HyperledgerDOMDataBrokerProvider(String nodeName, DOMSchemaService schemaService,
            ListeningExecutorService commitCoordinatorExecutor, ListeningExecutorService dtclExecutor, Contract c)
            throws Exception {
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

    private HyperledgerDataStore createConfigurationDatastore(LogicalDatastoreType type,
            ExecutorService dataTreeChangeListenerExecutor, DOMSchemaService schemaService, Contract c) {
        HyperledgerDataStore store = new HyperledgerDataStore(name, type, dataTreeChangeListenerExecutor,
                InMemoryDOMDataStoreConfigProperties.DEFAULT_MAX_DATA_CHANGE_LISTENER_QUEUE_SIZE, true, c);
        return store;
    }
}
