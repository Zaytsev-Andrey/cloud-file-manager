package ru.geekbrains;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.geekbrains.connection.ConnectionStatus;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ManagerServiceConnectionTest {
    public static ManagerService service;

    @BeforeClass
    public static void before() {
        service = new ManagerService(null, null);
    }

    @Test
    public void t1NewConnection() {
        Assert.assertEquals(ConnectionStatus.DISCONNECTED, service.getConnectionStatus());
    }

    @Test
    public void t2Connecting() {
        service.start();
        Assert.assertEquals(ConnectionStatus.CONNECTING, service.getConnectionStatus());
    }

    @Test
    public void t3Connected() {
        while (!service.isConnected());
        Assert.assertEquals(ConnectionStatus.CONNECTED, service.getConnectionStatus());
    }

    @Test
    public void t4Disconnected() {
        service.stop();
        while (service.isConnected());
        Assert.assertEquals(ConnectionStatus.DISCONNECTED, service.getConnectionStatus());
    }
}
