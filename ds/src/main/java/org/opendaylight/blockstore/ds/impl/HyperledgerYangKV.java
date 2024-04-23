package org.opendaylight.blockstore.ds.impl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.PreDestroy;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.SubmitException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.binfmt.NormalizedNodeDataInput;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeModification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("all")
class HyperledgerYangKV implements AutoCloseable {


    private static final Logger LOG = LoggerFactory.getLogger(HyperledgerYangKV.class);

    private final Contract contract;
    //private final byte[] prefixByteArray;
    //private final String prefixByteSequence;
    private final String name;

    HyperledgerYangKV(String name, String prefix, Contract smartContract) {
        this.name = name;
        this.contract = smartContract;
        //this.prefixByteArray = prefix.getBytes();
        //this.prefixByteSequence = "DS";
    }

    @Override
    @PreDestroy
    public void close() {
    }

    public HyperledgerTxn newTransaction() {
        return new HyperledgerTxn();
    }

    public void applyDelete(DataTreeModification dataTree, YangInstanceIdentifier key) throws HyperledgerException {
        dataTree.delete(key);
    }

    public void applyPut(DataTreeModification dataTree, String key, String value) throws HyperledgerException {
        try {
            var path = YangInstanceIdentifier.of(NodeIdentifier.create(QName.create(key)));
            dataTree.write(path, NormalizedNodeDataInput.newDataInput(
                new DataInputStream(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)))).
                readNormalizedNode());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String read(String key) {
        byte[] result = {0};
        try {
            result = contract.submitTransaction("ReadEntry", key);
        } catch (EndorseException e) {
            e.printStackTrace();
        } catch (SubmitException e) {
            e.printStackTrace();
        } catch (CommitStatusException e) {
            e.printStackTrace();
        } catch (CommitException e) {
            e.printStackTrace();
        }
        JsonObject jsonObject = JsonParser.parseString(new String(result, StandardCharsets.UTF_8)).getAsJsonObject();
        return jsonObject.get(key).getAsString();
    }

    public class HyperledgerTxn {

        HyperledgerTxn() {

        }

        public void put(YangInstanceIdentifier path, NormalizedNode data) throws HyperledgerException {
            try {
                contract.submitTransaction("PutEntry", path.toString(), data.toString());
            } catch (EndorseException | SubmitException | CommitStatusException | CommitException e) {
                e.printStackTrace();
            }
            LOG.info("{} TXN put: {} ➠ {}", name, path.toString(), data.toString());
        }

        public void delete(YangInstanceIdentifier path) throws HyperledgerException {
            try {
                contract.submitTransaction("DeleteEntry", path.toString());
            } catch (EndorseException | SubmitException | CommitStatusException | CommitException e) {
                e.printStackTrace();
            }
            LOG.info("{} TXN delete: {}", name, path.toString());
        }
    }
}
