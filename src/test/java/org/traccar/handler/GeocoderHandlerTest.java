package org.traccar.handler;

import org.junit.jupiter.api.Test;
import org.traccar.BaseTest;
import org.traccar.config.Config;
import org.traccar.geocoder.Geocoder;
import org.traccar.model.Device;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeocoderHandlerTest extends BaseTest {

    private Position createPosition(List<Long> geofenceIds) {
        Position position = new Position();
        position.setDeviceId(1);
        position.setLatitude(10);
        position.setLongitude(10);
        if (geofenceIds != null) {
            position.setGeofenceIds(geofenceIds);
        }
        return position;
    }

    @Test
    public void testGeofenceAddressEnabled() {

        var device = mock(Device.class);
        var attributes = new HashMap<String, Object>();
        attributes.put("geocoder.geofenceAddress", true);
        when(device.getAttributes()).thenReturn(attributes);

        var geofence = mock(Geofence.class);
        when(geofence.getName()).thenReturn("Company A");

        var config = mock(Config.class);
        var cacheManager = mock(CacheManager.class);
        when(cacheManager.getConfig()).thenReturn(config);
        when(cacheManager.getObject(eq(Device.class), anyLong())).thenReturn(device);
        when(cacheManager.getObject(eq(Geofence.class), anyLong())).thenReturn(geofence);

        var geocoder = mock(Geocoder.class);
        var handler = new GeocoderHandler(config, geocoder, cacheManager);

        Position position = createPosition(List.of(5L));

        handler.onPosition(position, filtered -> { });

        assertEquals("Company A", position.getAddress());
        verify(geocoder, never()).getAddress(anyDouble(), anyDouble(), any());

    }

    @Test
    public void testGeofenceAddressDisabled() {

        var device = mock(Device.class);
        when(device.getAttributes()).thenReturn(new HashMap<>());

        var config = mock(Config.class);
        var cacheManager = mock(CacheManager.class);
        when(cacheManager.getConfig()).thenReturn(config);
        when(cacheManager.getObject(eq(Device.class), anyLong())).thenReturn(device);

        var geocoder = mock(Geocoder.class);
        var handler = new GeocoderHandler(config, geocoder, cacheManager);

        Position position = createPosition(List.of(5L));

        handler.onPosition(position, filtered -> { });

        verify(geocoder).getAddress(anyDouble(), anyDouble(), any());

    }

}
