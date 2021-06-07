package ru.geekbrains.messages;

import java.io.Serializable;

/**
 * Package contain header for sends commands and body for sends bytes of file or bytes FileView.
 */
public class NetworkPackage implements Serializable {
    private PackageHeader header;
    private PackageBody body;

    public NetworkPackage(PackageHeader header, PackageBody body) {
        this.header = header;
        this.body = body;
    }

    public PackageHeader getHeader() {
        return header;
    }

    public PackageBody getBody() {
        return body;
    }
}
