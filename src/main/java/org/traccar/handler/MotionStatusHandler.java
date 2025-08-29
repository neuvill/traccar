package org.traccar.handler;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;
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
            Position lastPosition = cacheManager.getPosition(position.getDeviceId());
            String lastStatus = lastPosition != null ? (String) lastPosition.getAttributes().get("motionStatus") : null;
            String lastStatusChanged = lastPosition != null
            ? (String) lastPosition.getAttributes().get("motionStatusChanged")
            : null;
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

            if (lastStatus == null || !newStatus.equals(lastStatus)) {
            // update new status
                position.getAttributes().put("motionStatus", newStatus);
                position.getAttributes().put("motionStatusChanged", position.getFixTime() != null
                ? position.getFixTime() : new Date());
            } else {
            // preserve old values
                position.getAttributes().put("motionStatus", lastStatus);
                position.getAttributes().put("motionStatusChanged", lastStatusChanged);
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to update motion status", e);
        }
        callback.processed(false);
    }
}
