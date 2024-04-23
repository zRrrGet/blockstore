package org.opendaylight.blockstore.ds.impl;

final class HyperledgerException extends Exception {

    private static final long serialVersionUID = 1L;

    HyperledgerException(String message, Throwable cause) {
        super(message, cause);
    }

    HyperledgerException(String message) {
        super(message);
    }

}
