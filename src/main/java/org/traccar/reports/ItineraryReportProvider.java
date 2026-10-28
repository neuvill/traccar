package org.traccar.reports;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.PageSizeUnits;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.TextDirection;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.EscapeTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.security.PermissionsService;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.LocaleManager;
import org.traccar.database.MediaManager;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.helper.model.UserUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Server;
import org.traccar.model.User;
import org.traccar.reports.common.ReportImage;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.ItineraryEntry;
import org.traccar.reports.model.ItineraryRow;
import org.traccar.reports.model.ItinerarySummary;
import org.traccar.reports.model.ItineraryVehicleSection;
import org.traccar.reports.model.StopReportItem;
import org.traccar.reports.model.TripReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class ItineraryReportProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItineraryReportProvider.class);

    /** Shown in the masthead instead of a logo when the user hasn't uploaded one. */
    static final String DEFAULT_BRAND_NAME = "HM GLOBAL TECHNOLOGIES";

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;
    private final PermissionsService permissionsService;
    private final LocaleManager localeManager;
    private final VelocityEngine velocityEngine;
    private final MediaManager mediaManager;

    @Inject
    public ItineraryReportProvider(
            Config config, ReportUtils reportUtils, Storage storage, PermissionsService permissionsService,
            LocaleManager localeManager, VelocityEngine velocityEngine, MediaManager mediaManager) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
        this.permissionsService = permissionsService;
        this.localeManager = localeManager;
        this.velocityEngine = velocityEngine;
        this.mediaManager = mediaManager;
    }

    public Collection<ItineraryEntry> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException {
        reportUtils.checkPeriodLimit(from, to);
        List<ItineraryEntry> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(getEntries(device, from, to));
        }
        return result;
    }

    private List<ItineraryEntry> getEntries(Device device, Date from, Date to) throws StorageException {
        List<ItineraryEntry> entries = new ArrayList<>();
        for (TripReportItem trip : reportUtils.detectTripsAndStops(device, from, to, TripReportItem.class)) {
            entries.add(ItineraryEntry.trip(trip));
        }
        for (StopReportItem stop : reportUtils.detectTripsAndStops(device, from, to, StopReportItem.class)) {
            entries.add(ItineraryEntry.stop(stop));
        }
        entries.sort(Comparator.comparing(ItineraryEntry::getStartTime));
        return entries;
    }

    public void getPdf(
            OutputStream outputStream, long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException, IOException {
        String html = renderHtml(userId, deviceIds, groupIds, from, to);

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useDefaultPageSize(210, 297, PageSizeUnits.MM);

        // addresses from the geocoder mix Latin, Arabic and Tifinagh, so embed fonts covering all three
        // and enable bidi + Arabic shaping; the document itself stays left-to-right
        Path fonts = Path.of(config.getString(Keys.TEMPLATES_ROOT), "export", "itinerary", "fonts");
        builder.useFont(fonts.resolve("NotoSans-Regular.ttf").toFile(), "Noto Sans", 400, FontStyle.NORMAL, true);
        builder.useFont(fonts.resolve("NotoSans-Bold.ttf").toFile(), "Noto Sans", 700, FontStyle.NORMAL, true);
        builder.useFont(fonts.resolve("NotoSansArabic-Regular.ttf").toFile(),
                "Noto Sans Arabic", 400, FontStyle.NORMAL, true);
        builder.useFont(fonts.resolve("NotoSansArabic-Bold.ttf").toFile(),
                "Noto Sans Arabic", 700, FontStyle.NORMAL, true);
        builder.useFont(fonts.resolve("NotoSansTifinagh-Regular.ttf").toFile(),
                "Noto Sans Tifinagh", 400, FontStyle.NORMAL, true);
        builder.useFont(fonts.resolve("NotoSansTifinagh-Regular.ttf").toFile(),
                "Noto Sans Tifinagh", 700, FontStyle.NORMAL, true);
        builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
        builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
        builder.defaultTextDirection(TextDirection.LTR);
        builder.withHtmlContent(html, "");
        builder.toStream(outputStream);
        builder.run();
    }

    String renderHtml(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws StorageException, IOException {
        reportUtils.checkPeriodLimit(from, to);

        Server server = permissionsService.getServer();
        User user = permissionsService.getUser(userId);
        String language = UserUtil.getLanguage(server, user);
        String distanceUnit = UserUtil.getDistanceUnit(server, user);
        String speedUnit = UserUtil.getSpeedUnit(server, user);
        TimeZone timezone = UserUtil.getTimezone(server, user);

        // French and Arabic both use French-style number grouping; Arabic itself is postponed for now
        // (see project memory on the PDF report) pending a different RTL rendering approach.
        Locale numberLocale = "fr".equals(language) ? Locale.FRANCE : Locale.US;
        Locale textLocale = "fr".equals(language) ? Locale.FRENCH : Locale.US;

        List<Device> devices = new ArrayList<>(DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds));

        DateFormat timeFormat = new SimpleDateFormat("HH:mm");
        timeFormat.setTimeZone(timezone);
        DateFormat dayFormat = new SimpleDateFormat(
                "fr".equals(language) ? "EEEE d MMMM yyyy" : "EEEE, MMMM d, yyyy", textLocale);
        dayFormat.setTimeZone(timezone);
        // used for the end time when an entry (typically an overnight stop) ends on a later day
        DateFormat dayTimeFormat = new SimpleDateFormat(
                "fr".equals(language) ? "dd/MM HH:mm" : "MMM d HH:mm", textLocale);
        dayTimeFormat.setTimeZone(timezone);

        List<ItineraryVehicleSection> sections = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            Device device = devices.get(i);
            List<ItineraryEntry> entries = getEntries(device, from, to);

            ItineraryVehicleSection section = new ItineraryVehicleSection();
            section.setDeviceName(device.getName());
            section.setVehicleIndex(i + 1);
            section.setVehicleCount(devices.size());
            if (device.getGroupId() > 0) {
                Group group = storage.getObject(Group.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getGroupId())));
                if (group != null) {
                    section.setGroupName(group.getName());
                }
            }

            ItinerarySummary summary = summarize(entries);
            section.setDistance(formatDistance(summary.getDistance(), distanceUnit, numberLocale));
            section.setDrivingTime(formatDuration(summary.getDrivingTime()));
            section.setStoppedTime(formatDuration(summary.getStoppedTime()));
            section.setAverageSpeed(formatAverageSpeed(
                    summary.getDistance(), summary.getDrivingTime(), speedUnit, numberLocale));
            section.setTripCount(String.valueOf(summary.getTripCount()));
            section.setStopCount(String.valueOf(summary.getStopCount()));
            section.setOdometerStart(formatDistance(summary.getOdometerStart(), distanceUnit, numberLocale));
            section.setOdometerEnd(formatDistance(summary.getOdometerEnd(), distanceUnit, numberLocale));

            List<ItineraryRow> rows = new ArrayList<>();
            for (ItineraryEntry entry : entries) {
                rows.add(buildRow(entry, timeFormat, dayFormat, dayTimeFormat, textLocale,
                        distanceUnit, speedUnit, numberLocale));
            }
            section.setRows(rows);

            sections.add(section);
        }

        var translations = localeManager.getBundle(
                Path.of(config.getString(Keys.TEMPLATES_ROOT), "export", "itinerary", "i18n"), language);

        String folderId = "user-" + userId;
        String logoDataUri = loadImageDataUri(folderId, "logo");
        String stampDataUri = loadImageDataUri(folderId, "stamp");

        VelocityContext context = new VelocityContext();
        context.put("esc", new EscapeTool());
        context.put("translations", translations);
        context.put("sections", sections);
        context.put("generatedAt", formatGeneratedAt(timezone, textLocale));
        context.put("period", formatPeriod(from, to, timezone, language));
        if (logoDataUri != null) {
            context.put("logoDataUri", logoDataUri);
        } else {
            context.put("brandName", DEFAULT_BRAND_NAME);
        }
        if (stampDataUri != null) {
            context.put("stampDataUri", stampDataUri);
        }

        Path templateFile = localeManager.getTemplateFile(
                config.getString(Keys.TEMPLATES_ROOT), "export/itinerary", language, "ledger.vm");
        Template template = velocityEngine.getTemplate(templateFile.toString(), StandardCharsets.UTF_8.name());
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }

    private String loadImageDataUri(String folderId, String name) throws IOException {
        File file = mediaManager.findFile(folderId, name, ReportImage.EXTENSIONS);
        if (file == null) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        String extension;
        try {
            // a broken stored file must not break the whole report
            extension = ReportImage.validate(bytes);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Ignoring invalid report image {}", file, e);
            return null;
        }
        return "data:" + ReportImage.mimeType(extension) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private ItinerarySummary summarize(List<ItineraryEntry> entries) {
        ItinerarySummary summary = new ItinerarySummary();
        boolean first = true;
        for (ItineraryEntry entry : entries) {
            double startOdometer;
            double endOdometer;
            if (entry.isTrip()) {
                TripReportItem trip = entry.getTrip();
                summary.setDistance(summary.getDistance() + trip.getDistance());
                summary.setDrivingTime(summary.getDrivingTime() + trip.getDuration());
                summary.setTripCount(summary.getTripCount() + 1);
                startOdometer = trip.getStartOdometer();
                endOdometer = trip.getEndOdometer();
            } else {
                StopReportItem stop = entry.getStop();
                summary.setStoppedTime(summary.getStoppedTime() + stop.getDuration());
                summary.setStopCount(summary.getStopCount() + 1);
                startOdometer = stop.getStartOdometer();
                endOdometer = stop.getEndOdometer();
            }
            if (first) {
                summary.setOdometerStart(startOdometer);
                first = false;
            }
            summary.setOdometerEnd(endOdometer);
        }
        return summary;
    }

    private ItineraryRow buildRow(
            ItineraryEntry entry, DateFormat timeFormat, DateFormat dayFormat, DateFormat dayTimeFormat,
            Locale textLocale, String distanceUnit, String speedUnit, Locale locale) {
        ItineraryRow row = new ItineraryRow();
        row.setTrip(entry.isTrip());
        String startDay = dayFormat.format(entry.getStartTime());
        boolean sameDay = startDay.equals(dayFormat.format(entry.getEndTime()));
        row.setTimeRange(timeFormat.format(entry.getStartTime()) + " – "
                + (sameDay ? timeFormat : dayTimeFormat).format(entry.getEndTime()));
        row.setDay(capitalize(startDay, textLocale));
        row.setDuration(formatDuration(entry.getEndTime().getTime() - entry.getStartTime().getTime()));

        if (entry.isTrip()) {
            TripReportItem trip = entry.getTrip();
            row.setFromAddress(Objects.requireNonNullElse(trip.getStartAddress(), ""));
            row.setToAddress(Objects.requireNonNullElse(trip.getEndAddress(), ""));
            row.setDistance(formatDistance(trip.getDistance(), distanceUnit, locale));
            row.setSpeed(formatSpeedKnots(trip.getAverageSpeed(), speedUnit, locale));
            row.setOdometer(formatDistance(trip.getEndOdometer(), distanceUnit, locale));
        } else {
            StopReportItem stop = entry.getStop();
            row.setFromAddress(Objects.requireNonNullElse(stop.getAddress(), ""));
            row.setOdometer(formatDistance(stop.getEndOdometer(), distanceUnit, locale));
        }
        return row;
    }

    private static String formatDistance(double meters, String unit, Locale locale) {
        double value;
        String suffix;
        if ("mi".equals(unit)) {
            value = meters * 0.000621371;
            suffix = " mi";
        } else if ("nmi".equals(unit)) {
            value = meters * 0.000539957;
            suffix = " nmi";
        } else {
            value = meters * 0.001;
            suffix = " km";
        }
        return formatNumber(value, 2, locale) + suffix;
    }

    private static String formatSpeedKnots(double knots, String unit, Locale locale) {
        double value;
        String suffix;
        if ("mph".equals(unit)) {
            value = knots * 1.15078;
            suffix = " mph";
        } else if ("kn".equals(unit)) {
            value = knots;
            suffix = " kn";
        } else {
            value = knots * 1.852;
            suffix = " km/h";
        }
        return formatNumber(value, 1, locale) + suffix;
    }

    private static String formatAverageSpeed(double distanceMeters, long drivingTimeMs, String unit, Locale locale) {
        double hours = drivingTimeMs / 3600000.0;
        if (hours <= 0) {
            return formatNumber(0, 1, locale) + unitSuffix(unit);
        }
        double value;
        if ("mph".equals(unit)) {
            value = (distanceMeters * 0.000621371) / hours;
        } else if ("kn".equals(unit)) {
            value = (distanceMeters * 0.000539957) / hours;
        } else {
            value = (distanceMeters * 0.001) / hours;
        }
        return formatNumber(value, 1, locale) + unitSuffix(unit);
    }

    private static String unitSuffix(String speedUnit) {
        if ("mph".equals(speedUnit)) {
            return " mph";
        } else if ("kn".equals(speedUnit)) {
            return " kn";
        } else {
            return " km/h";
        }
    }

    private static String formatNumber(double value, int fractionDigits, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(fractionDigits);
        format.setMaximumFractionDigits(fractionDigits);
        return format.format(value);
    }

    private static String formatDuration(long millis) {
        long totalMinutes = millis / 60000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    static String formatPeriod(Date from, Date to, TimeZone timezone, String language) {
        DateFormat format = "fr".equals(language)
                ? new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH)
                : new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US);
        format.setTimeZone(timezone);
        return format.format(from) + " – " + format.format(to);
    }

    private static String capitalize(String text, Locale locale) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    private static String formatGeneratedAt(TimeZone timezone, Locale locale) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);
        format.setTimeZone(timezone);
        return format.format(new Date());
    }

}
