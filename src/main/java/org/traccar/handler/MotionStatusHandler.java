package org.traccar.handler;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Date;

public class MotionStatusHandler extends BasePositionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MotionStatusHandler.class);

    private final CacheManager cacheManager;

    @Inject
    public MotionStatusHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onPosition(Position position, Callback callback) {
        try {
            Device device = cacheManager.getObject(Device.class, position.getDeviceId());
            if (device == null) {
                callback.processed(false);
                return;
            }

            boolean ignition = Boolean.TRUE.equals(position.getAttributes().get("ignition"));
            boolean motion = Boolean.TRUE.equals(position.getAttributes().get("motion"));
            //double speed = position.getSpeed();

            String newStatus;
            if (motion) {
                newStatus = "moving";
            } else if (ignition) {
                newStatus = "idling";
            } else {
                newStatus = "parked";
            }

            if (!newStatus.equals(device.getMotionStatus())) {
                device.setMotionStatus(newStatus);
                device.setMotionStatusChanged(position.getFixTime() != null ? position.getFixTime() : new Date());

                storage.updateObject(device, new Request(
                new Columns.Include("motionStatus", "motionStatusChanged"),
                new Condition.Equals("id", device.getId())));
            }

            // Save in position attributes (persisted in DB)
            position.getAttributes().put("motionStatus", device.getMotionStatus());
            position.getAttributes().put("motionStatusChanged", device.getMotionStatusChanged());

        } catch (Exception e) {
            LOGGER.warn("Failed to update motion status", e);
        }
        callback.processed(false);
    }
}
