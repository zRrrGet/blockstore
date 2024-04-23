package org.opendaylight.blockstore.ds.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hyperledger.fabric.client.Contract;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreReadTransaction;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreReadWriteTransaction;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreWriteTransaction;
import org.opendaylight.blockstore.ds.impl.HyperledgerYangKV.HyperledgerTxn;
import org.opendaylight.blockstore.ds.inmemory.copypaste.InMemoryDOMDataStore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.tree.api.DataValidationFailedException;
import org.opendaylight.yangtools.yang.data.tree.api.ModificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("all")
public class HyperledgerDataStore extends InMemoryDOMDataStore {

    private static final Logger LOG = LoggerFactory.getLogger(HyperledgerDataStore.class);

    public static final String CONFIGURATION_PREFIX = "C";
    public static final String OPERATIONAL_PREFIX   = "O";

    private final Contract contract;
    private final HyperledgerYangKV kv;

    private boolean hasSchemaContext = false;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public HyperledgerDataStore(String name, LogicalDatastoreType type, ExecutorService dataChangeListenerExecutor,
            int maxDataChangeListenerQueueSize, boolean debugTransactions, Contract c) {
        super(name + "-" + prefixChar(type), dataChangeListenerExecutor, maxDataChangeListenerQueueSize,
                debugTransactions);
        this.contract = c;

        kv = new HyperledgerYangKV(getIdentifier(), prefix(type), contract);
    }

    @Override
    public DOMStoreReadTransaction newReadOnlyTransaction() {
        isInitialized();
        return super.newReadOnlyTransaction();
    }

    @Override
    public DOMStoreReadWriteTransaction newReadWriteTransaction() {
        isInitialized();
        return super.newReadWriteTransaction();
    }

    @Override
    public DOMStoreWriteTransaction newWriteOnlyTransaction() {
        isInitialized();
        return super.newWriteOnlyTransaction();
    }

    public void init(long rev) throws Exception {
        if (!hasSchemaContext) {
            throw new IllegalStateException("onGlobalContextUpdated() not yet called");
        }
        this.isInitialized.set(true);
    }

    @Override
    public void close() {
    }

    private static char prefixChar(LogicalDatastoreType type) {
        return (char) prefix(type).getBytes()[0];
    }

    private static String prefix(LogicalDatastoreType type) {
        return type.equals(LogicalDatastoreType.CONFIGURATION) ? CONFIGURATION_PREFIX : OPERATIONAL_PREFIX;
    }

    @Override
    protected synchronized void commit(DataTreeCandidate candidate) {
        isInitialized();
        if (!candidate.getRootPath().equals(YangInstanceIdentifier.of())) {
            LOG.error("DataTreeCandidate: YangInstanceIdentifier path={}", candidate.getRootPath());
            throw new IllegalArgumentException("I've not learnt how to deal with DataTreeCandidate where "
                    + "root path != YangInstanceIdentifier.EMPTY yet - will you teach me? ;)");
        }

        LOG.info("{} commit: DataTreeCandidate={}", getIdentifier(), candidate);
        print("", candidate.getRootNode());

        try {
            HyperledgerTxn kvTx = kv.newTransaction();
            sendToHyperledger(kvTx, candidate, candidate.getRootPath(), candidate.getRootNode());
        } catch (HyperledgerException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    private void sendToHyperledger(HyperledgerTxn kvTx, DataTreeCandidate candidate, YangInstanceIdentifier base,
            DataTreeCandidateNode node) throws IllegalArgumentException, HyperledgerException {
        YangInstanceIdentifier newBase = candidate.getRootNode().equals(node) ? base : base.node(node.name());

        ModificationType modificationType = node.modificationType();
        switch (modificationType) {
            case WRITE:
            case APPEARED:
                kvTx.put(newBase,
                        node.getDataAfter());
                break;

            case DELETE:
            case DISAPPEARED:
                kvTx.delete(newBase);
                break;

            case UNMODIFIED:
            case SUBTREE_MODIFIED:
                break;

        }

        for (DataTreeCandidateNode childNode : node.childNodes()) {
            sendToHyperledger(kvTx, candidate, newBase, childNode);
        }
    }

    private void print(String indent, DataTreeCandidateNode node) {
        if (LOG.isInfoEnabled()) {
            LOG.info("{}{} DataTreeCandidateNode: modificationType={}, PathArgument identifier={}",
                    indent, getIdentifier(), node.modificationType(), getIdentifierAsString(node));
            LOG.info("{}{}   dataAfter = {}", indent, getIdentifier(), node.getDataAfter());

            for (DataTreeCandidateNode childNode : node.childNodes()) {
                print(indent + "    ", childNode);
            }
        }
    }

    private static String getIdentifierAsString(DataTreeCandidateNode node) {
        try {
            return node.name().toString();
        } catch (IllegalStateException e) {
            return "-ROOT-";
        }
    }

    private void isInitialized() {
        if (!isInitialized.get()) {
            throw new IllegalStateException("init() not yet called");
        }
    }
}
