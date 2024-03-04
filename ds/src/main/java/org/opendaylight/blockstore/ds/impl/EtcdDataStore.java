/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.blockstore.ds.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hyperledger.fabric.client.Contract;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreReadTransaction;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreReadWriteTransaction;
import org.opendaylight.mdsal.dom.spi.store.DOMStoreWriteTransaction;
import org.opendaylight.blockstore.ds.impl.EtcdYangKV.EtcdTxn;
import org.opendaylight.blockstore.ds.inmemory.copypaste.InMemoryDOMDataStore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.tree.api.DataValidationFailedException;
import org.opendaylight.yangtools.yang.data.tree.api.ModificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ODL DOM Data Store implementation based on etcd.
 *
 * @author Michael Vorburger.ch
 */
@SuppressWarnings("all")
public class EtcdDataStore extends InMemoryDOMDataStore {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdDataStore.class);

    public static final String CONFIGURATION_PREFIX = "C"; // 67
    public static final String OPERATIONAL_PREFIX   = "O"; // 79

    // This flag could later be dynamic instead of fixed hard-coded, to optionally
    // support very fast reads with eventual instead of strong consistency.  We could do this either
    // globally and have different data stores (and, ultimately DataBroker), or per transaction.
    //private final boolean isStronglyConsistent = true;

    private final Contract contract;
    private final EtcdYangKV kv;

    private boolean hasSchemaContext = false;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public EtcdDataStore(String name, LogicalDatastoreType type, ExecutorService dataChangeListenerExecutor,
            int maxDataChangeListenerQueueSize, boolean debugTransactions, Contract c) {
        super(name + "-" + prefixChar(type), dataChangeListenerExecutor, maxDataChangeListenerQueueSize,
                debugTransactions);
        this.contract = c;

        kv = new EtcdYangKV(getIdentifier(), prefix(type), contract);
    }

    /**
     * On start-up, read back current persistent state from etcd as initial DataTree content.
     * @param rev the etcd Revision number to load
     * @throws DataValidationFailedException
     * @throws EtcdException if loading failed
     */
    // private void initialLoad(long rev) throws EtcdException {
    //     apply(mod -> kv.readAllInto(rev, mod));
    // }

    // private void apply(Consumer<DataTreeModification> function) throws DataValidationFailedException {
    //     // TODO requires https://git.opendaylight.org/gerrit/#/c/73482/ which makes dataTree protected instead of private
    //     DataTreeModification mod = dataTree.takeSnapshot().newModification();
    //     function.accept(mod);
    //     mod.ready();

    //     dataTree.validate(mod);
    //     DataTreeCandidate candidate = dataTree.prepare(mod);
    //     dataTree.commit(candidate);

    //     // also requires https://git.opendaylight.org/gerrit/#/c/73217/ which adds a protected notifyListeners to InMemoryDOMDataStore
    //     notifyListeners(candidate);

    //     LOG.info("{} applied DataTreeModification={}, DataTreeCandidate={}", getIdentifier(), mod, candidate);
    // }

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
        //initialLoad();
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
    // requires https://git.opendaylight.org/gerrit/#/c/73208/ :-( or figure out if we can hook into InMemoryDOMDataStore via a commit cohort?!
    protected synchronized void commit(DataTreeCandidate candidate) {
        isInitialized();
        if (!candidate.getRootPath().equals(YangInstanceIdentifier.of())) {
            LOG.error("DataTreeCandidate: YangInstanceIdentifier path={}", candidate.getRootPath());
            throw new IllegalArgumentException("I've not learnt how to deal with DataTreeCandidate where "
                    + "root path != YangInstanceIdentifier.EMPTY yet - will you teach me? ;)");
        }

        LOG.info("{} commit: DataTreeCandidate={}", getIdentifier(), candidate);
        print("", candidate.getRootNode());

        // TODO make InMemoryDOMDataStore.commit(DataTreeCandidate) return ListenableFuture<Void> instead of void,
        // and then InMemoryDOMStoreThreePhaseCommitCohort.commit() return store.commit(candidate) instead of SUCCESS,
        // and then do this:
//        sendToEtcd(candidate.getRootNode()).thenRun(() -> super.commit(candidate)).exceptionally(throwable -> {
//            LOG.error("sendToEtcd failed", throwable);
//            return null;
//        });
        // but for now let's throw the entire nice async-ity over board and just do:
        try {
            EtcdTxn kvTx = kv.newTransaction();
            sendToEtcd(kvTx, candidate, candidate.getRootPath(), candidate.getRootNode());
        } catch (EtcdException | IllegalArgumentException e) {
            // TODO This is ugly, wrong, and just temporary.. but see above, how to better return problems here?
            throw new RuntimeException(e);
        }

        // We do *NOT* super.commit(candidate), because we don't want to immediately/directly apply changes,
        // because we let the watcher listener do this - for ourselves here where we initiated the change, as well as
        // on all other remote nodes which listen to changes.  It seems tempting to optimize and for our own
        // node just apply ourselves, instead of going through the listener, but this causes
        // IllegalStateException: "Store tree ... and candidate base ... differ.", because we would apply
        // everything twice, because the watcher sends us back our own operations;
        // see also https://github.com/coreos/jetcd/issues/343.
    }

    @SuppressWarnings("checkstyle:MissingSwitchDefault") // http://errorprone.info/bugpattern/UnnecessaryDefaultInEnumSwitch
    private void sendToEtcd(EtcdTxn kvTx, DataTreeCandidate candidate, YangInstanceIdentifier base,
            DataTreeCandidateNode node) throws IllegalArgumentException, EtcdException {
        YangInstanceIdentifier newBase = candidate.getRootNode().equals(node) ? base : base.node(node.name());

        ModificationType modificationType = node.modificationType();
        switch (modificationType) {
            case WRITE:
            case APPEARED: // TODO is it right to treat APPEARED like WRITE here?
                kvTx.put(newBase,
                        node.getDataAfter());
                break;

            case DELETE:
            case DISAPPEARED: // TODO is it right to treat DISAPPEARED like DELETE here?
                kvTx.delete(newBase);
                break;

            case UNMODIFIED:
            case SUBTREE_MODIFIED:
                // ignore
                break;

            // no default, as error-prone protects us, see http://errorprone.info/bugpattern/UnnecessaryDefaultInEnumSwitch
        }

        for (DataTreeCandidateNode childNode : node.childNodes()) {
            sendToEtcd(kvTx, candidate, newBase, childNode);
        }
    }

    private void print(String indent, DataTreeCandidateNode node) {
        if (LOG.isInfoEnabled()) {
            LOG.info("{}{} DataTreeCandidateNode: modificationType={}, PathArgument identifier={}",
                    indent, getIdentifier(), node.modificationType(), getIdentifierAsString(node));
            // LOG.info("{}  dataBefore= {}", indent, node.getDataBefore());
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
            // just debugging code; not intended for production
            return "-ROOT-";
        }
    }

    private void isInitialized() {
        if (!isInitialized.get()) {
            throw new IllegalStateException("init() not yet called");
        }
    }
}
