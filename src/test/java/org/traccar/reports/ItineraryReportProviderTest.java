package org.traccar.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.BaseTest;
import org.traccar.api.security.PermissionsService;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.LocaleManager;
import org.traccar.database.MediaManager;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.model.Server;
import org.traccar.model.User;
import org.traccar.reports.common.ReportUtils;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ItineraryReportProviderTest extends BaseTest {

    private Storage storage;
    private Config config;
    private PermissionsService permissionsService;
    private VelocityEngine velocityEngine;
    private LocaleManager localeManager;
    private ReportUtils reportUtils;
    private MediaManager mediaManager;

    @BeforeEach
    public void init() throws StorageException {
        storage = mock(Storage.class);

        Device device = mock(Device.class);
        when(device.getId()).thenReturn(1L);
        when(device.getName()).thenReturn("Fourgon 12");
        when(device.getGroupId()).thenReturn(0L);
        Device device2 = mock(Device.class);
        when(device2.getId()).thenReturn(2L);
        when(device2.getName()).thenReturn("Camion 07");
        when(device2.getGroupId()).thenReturn(0L);
        when(storage.getObject(eq(Device.class), any())).thenReturn(device);
        when(storage.getObjects(eq(Device.class), any())).thenReturn(List.of(device, device2));

        config = mock(Config.class);
        when(config.getString(Keys.TEMPLATES_ROOT)).thenReturn("templates");
        when(config.getLong(Keys.REPORT_PERIOD_LIMIT)).thenReturn(0L);
        when(config.getLong(Keys.REPORT_FAST_THRESHOLD)).thenReturn(86400L);
        when(config.getString(Keys.WEB_LOCALIZATION_PATH)).thenReturn("templates/translations");

        permissionsService = mock(PermissionsService.class);
        when(permissionsService.getServer()).thenReturn(new Server());
        when(permissionsService.getUser(1L)).thenReturn(new User());

        Properties properties = new Properties();
        properties.setProperty("resource.loader.file.path", "templates/");
        velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);

        localeManager = new LocaleManager(config, new ObjectMapper());

        reportUtils = new ReportUtils(config, storage, permissionsService, velocityEngine, null);

        mediaManager = mock(MediaManager.class);
    }

    private Date date(String time) throws ParseException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.parse(time);
    }

    private Position position(long id, String time, double speed, double totalDistance) throws ParseException {
        return position(id, time, speed, totalDistance, null);
    }

    private Position position(
            long id, String time, double speed, double totalDistance, String address) throws ParseException {
        Position position = new Position();
        position.setId(id);
        position.setTime(date(time));
        position.setValid(true);
        position.setSpeed(speed);
        position.set(Position.KEY_MOTION, speed > 0);
        position.set(Position.KEY_TOTAL_DISTANCE, totalDistance);
        position.setAddress(address);
        return position;
    }

    @Test
    public void testPdfRendersForBothLanguages() throws Exception {
        when(storage.getObjectsStream(eq(Position.class), any())).thenAnswer(invocation -> Stream.of(
                position(1, "2026-10-15 08:19:00.000", 0, 0, "Rue de l'Église & Fils, Alger"),
                position(2, "2026-10-15 08:20:00.000", 0, 0, "Rue de l'Église & Fils, Alger"),
                position(3, "2026-10-15 08:21:00.000", 10, 0, "Rue de l'Église & Fils, Alger"),
                position(4, "2026-10-15 09:38:00.000", 10, 16060, "RN 11 <Nord>, Boumerdès"),
                position(5, "2026-10-15 09:39:00.000", 0, 16060, "RN 11 <Nord>, Boumerdès"),
                position(6, "2026-10-15 11:26:00.000", 0, 16060, "RN 11 <Nord>, Boumerdès")));

        ItineraryReportProvider provider = new ItineraryReportProvider(
                config, reportUtils, storage, permissionsService, localeManager, velocityEngine, mediaManager);

        Date from = date("2026-10-15 00:00:00.000");
        Date to = date("2026-10-15 23:59:59.000");

        for (String language : List.of("en", "fr")) {
            User user = new User();
            user.set("language", language);
            when(permissionsService.getUser(1L)).thenReturn(user);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            provider.getPdf(output, 1L, List.of(), List.of(), from, to);

            byte[] bytes = output.toByteArray();
            assertTrue(bytes.length > 500, "PDF output should not be empty for language " + language);
            assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        }
    }

    @Test
    public void testLogoOnEveryPageStampOnlyOnLast() throws Exception {
        when(storage.getObjectsStream(eq(Position.class), any())).thenAnswer(invocation -> Stream.of(
                position(1, "2026-10-15 08:19:00.000", 0, 0),
                position(2, "2026-10-15 08:20:00.000", 0, 0),
                position(3, "2026-10-15 08:21:00.000", 10, 0),
                position(4, "2026-10-15 09:38:00.000", 10, 16060)));

        byte[] pngBytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        File logoFile = File.createTempFile("logo", ".png");
        Files.write(logoFile.toPath(), pngBytes);
        File stampFile = File.createTempFile("stamp", ".png");
        Files.write(stampFile.toPath(), pngBytes);
        logoFile.deleteOnExit();
        stampFile.deleteOnExit();

        when(mediaManager.findFile(eq("user-1"), eq("logo"), any(String[].class))).thenReturn(logoFile);
        when(mediaManager.findFile(eq("user-1"), eq("stamp"), any(String[].class))).thenReturn(stampFile);

        ItineraryReportProvider provider = new ItineraryReportProvider(
                config, reportUtils, storage, permissionsService, localeManager, velocityEngine, mediaManager);

        when(permissionsService.getUser(1L)).thenReturn(new User());

        Date from = date("2026-10-15 00:00:00.000");
        Date to = date("2026-10-15 23:59:59.000");
        String html = provider.renderHtml(1L, List.of(), List.of(), from, to);

        long logoOccurrences = html.split("class=\"logo\"", -1).length - 1;
        long stampOccurrences = html.split("class=\"stamp\"", -1).length - 1;

        assertEquals(2, logoOccurrences, "logo should appear once per vehicle page");
        assertEquals(1, stampOccurrences, "stamp should appear only once, on the last page");
        assertTrue(html.contains("data:image/png;base64,"));
    }

}
