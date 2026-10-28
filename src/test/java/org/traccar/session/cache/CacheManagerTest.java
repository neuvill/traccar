package org.traccar.session.cache;

import org.junit.jupiter.api.Test;
import org.traccar.broadcast.BroadcastService;
import org.traccar.config.Config;
import org.traccar.model.Device;
import org.traccar.model.Permission;
import org.traccar.model.Position;
import org.traccar.model.Server;
import org.traccar.model.User;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CacheManagerTest {

    @Test
    public void testReplacePositionWithSameFixTime() throws Exception {

        Storage storage = mock(Storage.class);
        BroadcastService broadcastService = mock(BroadcastService.class);
        Device device = new Device();
        device.setId(1);
        when(storage.getObject(eq(Server.class), any(Request.class))).thenReturn(new Server());
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(device);
        when(storage.getPermissions(eq(Device.class), any())).thenReturn(List.of());
        when(storage.getPermissions(eq(User.class), eq(Device.class))).thenReturn(List.of());
        doNothing().when(broadcastService).registerListener(any());

        CacheManager cacheManager = new CacheManager(new Config(), storage, broadcastService);

        long deviceId = 1;
        Object key = new Object();
        cacheManager.addDevice(deviceId, key);

        long fixTime = System.currentTimeMillis();

        Position first = new Position();
        first.setDeviceId(deviceId);
        first.setFixTime(new Date(fixTime));

        Position replacement = new Position();
        replacement.setDeviceId(deviceId);
        replacement.setFixTime(new Date(fixTime));

        cacheManager.updatePosition(first);
        cacheManager.updatePosition(replacement);

        assertSame(replacement, cacheManager.getPosition(deviceId));
    }

    private Storage storage;

    private CacheManager createCacheManager(long[]... links) throws Exception {
        storage = mock(Storage.class);
        BroadcastService broadcastService = mock(BroadcastService.class);
        when(storage.getObject(eq(Server.class), any(Request.class))).thenReturn(new Server());
        when(storage.getObject(eq(Device.class), any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            Device device = new Device();
            device.setId((Long) ((Condition.Equals) request.getCondition()).getValue());
            return device;
        });
        when(storage.getPermissions(eq(Device.class), eq(Device.class))).thenReturn(Arrays.stream(links)
                .map(link -> new Permission(Device.class, link[0], Device.class, link[1]))
                .toList());
        doNothing().when(broadcastService).registerListener(any());
        return new CacheManager(new Config(), storage, broadcastService);
    }

    private void assertCached(CacheManager cacheManager, long... deviceIds) {
        for (long deviceId : deviceIds) {
            assertNotNull(cacheManager.getObject(Device.class, deviceId), "device " + deviceId + " not cached");
        }
    }

    private void assertNotCached(CacheManager cacheManager, long... deviceIds) {
        for (long deviceId : deviceIds) {
            assertNull(cacheManager.getObject(Device.class, deviceId), "device " + deviceId + " still cached");
        }
    }

    @Test
    public void testSelfLinkedDevice() throws Exception {
        CacheManager cacheManager = createCacheManager(new long[] {1, 1});
        Object key = new Object();

        cacheManager.addDevice(1, key);
        assertCached(cacheManager, 1);
        verify(storage, times(1)).getObject(eq(Device.class), any(Request.class));

        cacheManager.removeDevice(1, key);
        assertNotCached(cacheManager, 1);
    }

    @Test
    public void testMutuallyLinkedDevices() throws Exception {
        CacheManager cacheManager = createCacheManager(new long[] {1, 2}, new long[] {2, 1});
        Object key1 = new Object();
        Object key2 = new Object();

        cacheManager.addDevice(1, key1);
        assertCached(cacheManager, 1, 2);
        verify(storage, times(2)).getObject(eq(Device.class), any(Request.class));

        // device 2 has its own session, so both stay cached after device 1 disconnects
        cacheManager.addDevice(2, key2);
        cacheManager.removeDevice(1, key1);
        assertCached(cacheManager, 1, 2);

        // only the links between them are left, which must not keep them cached
        cacheManager.removeDevice(2, key2);
        assertNotCached(cacheManager, 1, 2);
    }

    @Test
    public void testDeviceLinkCycle() throws Exception {
        CacheManager cacheManager = createCacheManager(new long[] {1, 2}, new long[] {2, 3}, new long[] {3, 1});
        Object key = new Object();

        cacheManager.addDevice(1, key);
        assertCached(cacheManager, 1, 2, 3);
        verify(storage, times(3)).getObject(eq(Device.class), any(Request.class));

        cacheManager.removeDevice(1, key);
        assertNotCached(cacheManager, 1, 2, 3);

        // loading again after a full release works the same way
        cacheManager.addDevice(2, key);
        assertCached(cacheManager, 1, 2, 3);
        cacheManager.removeDevice(2, key);
        assertNotCached(cacheManager, 1, 2, 3);
    }

    @Test
    public void testLinkedDeviceStaysWhileReferenced() throws Exception {
        CacheManager cacheManager = createCacheManager(new long[] {1, 2});
        Object key1 = new Object();
        Object key2 = new Object();

        cacheManager.addDevice(1, key1);
        cacheManager.addDevice(2, key2);
        cacheManager.removeDevice(2, key2);
        assertCached(cacheManager, 1, 2);

        cacheManager.removeDevice(1, key1);
        assertNotCached(cacheManager, 1, 2);
    }

    @Test
    public void testFailedLoadIsRolledBack() throws Exception {
        CacheManager cacheManager = createCacheManager(new long[] {1, 2}, new long[] {2, 1});
        when(storage.getPermissions(eq(Device.class), eq(Device.class))).thenThrow(new StorageException("test"));
        Object key = new Object();

        assertThrows(StorageException.class, () -> cacheManager.addDevice(1, key));
        assertNotCached(cacheManager, 1, 2);
        verify(storage, never()).getObject(eq(Position.class), any(Request.class));
    }

}
