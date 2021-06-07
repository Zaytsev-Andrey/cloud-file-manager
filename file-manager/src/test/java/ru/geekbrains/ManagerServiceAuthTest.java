package ru.geekbrains;

import org.junit.*;
import org.junit.runners.MethodSorters;
import ru.geekbrains.connection.AuthStatus;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ManagerServiceAuthTest {
    public static ManagerService service;

    @BeforeClass
    public static void before() {
        service = new ManagerService(null, null);
        service.start();
        while (!service.isConnected());
    }

    @Test
    public void t1NotAuthenticated() {
        Assert.assertEquals(AuthStatus.NOT_AUTHENTICATED, service.getAuthStatus());
    }

    @Test
    public void t2AuthenticatedFail() {
        service.login("test", "");
        while (service.getAuthStatus().equals(AuthStatus.NOT_AUTHENTICATED));
        Assert.assertEquals(AuthStatus.AUTHENTICATION_FAIL, service.getAuthStatus());
    }

    @Test
    public void t3Authenticated() {
        service.login("test", "test");
        while (!service.isAuthenticated());
        Assert.assertEquals(AuthStatus.AUTHENTICATED, service.getAuthStatus());
    }

    @AfterClass
    public static void after() {
        service.stop();
    }
}
