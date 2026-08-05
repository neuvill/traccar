package org.traccar.handler.events;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

public class IdlingEventHandler extends BaseEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdlingEventHandler.class);

    private static final String STATUS_IDLING = "idling";
    private static final String ATTRIBUTE_DURATION = "duration";
    private static final String ATTRIBUTE_IDLING_SINCE = "idlingSince";
    private static final String ATTRIBUTE_IDLING_EVENT_ID = "idlingEventId";

    private final CacheManager cacheManager;
    private final Storage storage;
    private final long minimalDuration;

    @Inject
    public IdlingEventHandler(Config config, CacheManager cacheManager, Storage storage) {
        this.cacheManager = cacheManager;
        this.storage = storage;
        minimalDuration = config.getLong(Keys.EVENT_IDLE_MINIMAL_DURATION) * 1000;
    }

    @Override
    public void onPosition(Position position, Callback callback) {

        Device device = cacheManager.getObject(Device.class, position.getDeviceId());
        if (device == null || !PositionUtil.isLatest(cacheManager, position)) {
            return;
        }

        String motionStatus = position.getString("motionStatus");
        if (motionStatus == null || position.getFixTime() == null) {
            return;
        }

        long fixTime = position.getFixTime().getTime();
        boolean changed = false;

        if (STATUS_IDLING.equals(motionStatus)) {
            if (!device.hasAttribute(ATTRIBUTE_IDLING_SINCE)) {
                device.set(ATTRIBUTE_IDLING_SINCE, fixTime);
                changed = true;
            } else if (device.getLong(ATTRIBUTE_IDLING_EVENT_ID) == 0) {
                long idlingSince = device.getLong(ATTRIBUTE_IDLING_SINCE);
                if (fixTime - idlingSince >= minimalDuration) {
                    Event event = new Event(Event.TYPE_DEVICE_IDLE, position);
                    event.set(ATTRIBUTE_DURATION, fixTime - idlingSince);
                    callback.eventDetected(event);
                    device.set(ATTRIBUTE_IDLING_EVENT_ID, event.getId());
                    changed = true;
                }
            }
        } else if (device.hasAttribute(ATTRIBUTE_IDLING_SINCE)) {
            long idlingEventId = device.getLong(ATTRIBUTE_IDLING_EVENT_ID);
            if (idlingEventId != 0) {
                closeEvent(idlingEventId, fixTime - device.getLong(ATTRIBUTE_IDLING_SINCE));
            }
            device.removeAttribute(ATTRIBUTE_IDLING_SINCE);
            device.removeAttribute(ATTRIBUTE_IDLING_EVENT_ID);
            changed = true;
        }

        if (changed) {
            try {
                storage.updateObject(device, new Request(
                        new Columns.Include("attributes"),
                        new Condition.Equals("id", device.getId())));
            } catch (StorageException e) {
                LOGGER.warn("Update device idling state error", e);
            }
        }
    }

    private void closeEvent(long eventId, long duration) {
        try {
            Event event = storage.getObject(Event.class, new Request(
                    new Columns.All(), new Condition.Equals("id", eventId)));
            if (event != null) {
                event.set(ATTRIBUTE_DURATION, duration);
                storage.updateObject(event, new Request(
                        new Columns.Include("attributes"),
                        new Condition.Equals("id", eventId)));
            }
        } catch (StorageException e) {
            LOGGER.warn("Update idling event error", e);
        }
    }

}
