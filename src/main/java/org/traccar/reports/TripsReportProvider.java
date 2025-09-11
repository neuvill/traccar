package org.traccar.reports;

import org.apache.poi.ss.util.WorkbookUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.DeviceReportSection;
import org.traccar.reports.model.TripReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class TripsReportProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripsReportProvider.class);

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public TripsReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    public Collection<TripReportItem> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException {

        LOGGER.info("TripsReportProvider.getObjects called - userId={}, deviceIds={}, groupIds={}, from={}, to={}",
                userId, deviceIds, groupIds, from, to);

        reportUtils.checkPeriodLimit(from, to);

        ArrayList<TripReportItem> result = new ArrayList<>();
        Collection<Device> devices = DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds);
        LOGGER.info("Found {} accessible devices", devices.size());

        for (Device device : devices) {
            LOGGER.info("Detecting trips for deviceId={} ({}) from {} to {}",
                    device.getId(), device.getName(), from, to);
            LOGGER.info("DETECTING TRIPS AND STOPS");
            Collection<TripReportItem> trips = reportUtils.detectTripsAndStops(device, from, to, TripReportItem.class);

            LOGGER.info("Device {} ({}) - detected {} trips", device.getId(), device.getName(), trips.size());

            for (TripReportItem trip : trips) {
                LOGGER.info("Trip for device {} - start={}, end={}, duration={}s, distance={}m",
                        device.getName(), trip.getStartTime(), trip.getEndTime(),
                        trip.getDuration(), trip.getDistance());
            }

            result.addAll(trips);
        }

        LOGGER.info("Total trips returned: {}", result.size());
        return result;
    }

    public void getExcel(OutputStream outputStream,
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException, IOException {

        LOGGER.info("TripsReportProvider.getExcel called - userId={}, deviceIds={}, groupIds={}, from={}, to={}",
                userId, deviceIds, groupIds, from, to);

        reportUtils.checkPeriodLimit(from, to);

        ArrayList<DeviceReportSection> devicesTrips = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        Collection<Device> devices = DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds);

        LOGGER.info("Found {} accessible devices for Excel report", devices.size());

        for (Device device : devices) {
            LOGGER.info("Generating Excel trips for deviceId={} ({})", device.getId(), device.getName());

            Collection<TripReportItem> trips = reportUtils.detectTripsAndStops(device, from, to, TripReportItem.class);

            LOGGER.info("Device {} ({}) - detected {} trips", device.getId(), device.getName(), trips.size());

            DeviceReportSection deviceTrips = new DeviceReportSection();
            deviceTrips.setDeviceName(device.getName());
            sheetNames.add(WorkbookUtil.createSafeSheetName(deviceTrips.getDeviceName()));

            if (device.getGroupId() > 0) {
                Group group = storage.getObject(Group.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getGroupId())));
                if (group != null) {
                    LOGGER.info("Device {} belongs to group {}", device.getName(), group.getName());
                    deviceTrips.setGroupName(group.getName());
                }
            }

            deviceTrips.setObjects(trips);
            devicesTrips.add(deviceTrips);
        }

        File file = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", "trips.xlsx").toFile();
        try (InputStream inputStream = new FileInputStream(file)) {
            var context = reportUtils.initializeContext(userId);
            context.putVar("devices", devicesTrips);
            context.putVar("sheetNames", sheetNames);
            context.putVar("from", from);
            context.putVar("to", to);
            reportUtils.processTemplateWithSheets(inputStream, outputStream, context);
        }

        LOGGER.info("Excel report generation finished successfully");
    }

}
