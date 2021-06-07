package ru.geekbrains.messages;

import java.io.Serializable;

/**
 * Contains bytes of file or bytes FileView
 */
public class PackageBody implements Serializable {
    private Object[] objectBody;
    private byte[] byteBody;

    public PackageBody(Object[] objectBody) {
        this.objectBody = objectBody;
    }

    public PackageBody(byte[] byteBody) {
        this.byteBody = byteBody;
    }

    public Object[] getObjectBody() {
        return objectBody;
    }

    public byte[] getByteBody() {
        return byteBody;
    }
}
