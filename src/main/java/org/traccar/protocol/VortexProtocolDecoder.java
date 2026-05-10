/*
 * Copyright 2026 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.Protocol;
import org.traccar.helper.BitUtil;
import org.traccar.helper.DateBuilder;
import org.traccar.model.Position;
import org.traccar.session.DeviceSession;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.SocketAddress;

public class VortexProtocolDecoder extends BaseProtocolDecoder {

    private static final String LOGIN_PREFIX = "LICORNE.VORTEX.";

    public VortexProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private static Integer parseInteger(String value) {
        return value != null && !value.isEmpty() ? Integer.parseInt(value) : null;
    }

    private static Long parseLong(String value) {
        return value != null && !value.isEmpty() ? Long.parseLong(value) : null;
    }

    private static Double parseDouble(String value) {
        return value != null && !value.isEmpty() ? Double.parseDouble(value) : null;
    }

    private static void setOptional(Position position, String key, String value) {
        if (value != null) {
            position.set(key, value);
        }
    }

    private static void setOptional(Position position, String key, Integer value) {
        if (value != null) {
            position.set(key, value);
        }
    }

    private static void setOptional(Position position, String key, Double value) {
        if (value != null) {
            position.set(key, value);
        }
    }

    private Object decodeLogin(Channel channel, SocketAddress remoteAddress, String sentence) {

        JsonObject json = Json.createReader(new StringReader(sentence.substring(LOGIN_PREFIX.length()))).readObject();
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, json.getString("IMEI"));
        if (deviceSession == null) {
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());
        getLastLocation(position, null);

        setOptional(position, Position.KEY_ICCID, json.getString("CCID", null));
        setOptional(position, Position.KEY_VERSION_FW, json.getString("Firmware", null));
        setOptional(position, "config", json.getString("Config", null));
        setOptional(position, "carModel", json.getString("CarModel", null));
        setOptional(position, "carModelVersion", json.getString("CarModelVersion", null));
        setOptional(position, Position.KEY_VIN, json.getString("VIN", null));

        return position;
    }

    private void decodeInput(Position position, int input) {
        position.set(Position.KEY_INPUT, input);
        position.set(Position.PREFIX_IN + 1, BitUtil.check(input, 0));
        position.set(Position.PREFIX_IN + 2, BitUtil.check(input, 1));
        position.set("towing", BitUtil.check(input, 5));
        position.set(Position.KEY_IGNITION, BitUtil.check(input, 7));
    }

    private void decodeEvents(Position position, int events) {
        position.set(Position.KEY_EVENT, events);
        if (BitUtil.check(events, 0)) {
            position.addAlarm(Position.ALARM_OVERSPEED);
        }
        if (BitUtil.check(events, 4)) {
            position.addAlarm(Position.ALARM_TOW);
        }
    }

    private Object decodeData(Channel channel, SocketAddress remoteAddress, String sentence) {

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress);
        if (deviceSession == null) {
            return null;
        }

        String[] values = sentence.substring(1).split(",", -1);
        if (values.length < 16) {
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        String[] time = values[3].split(":", -1);
        if (time.length != 3) {
            return null;
        }
        position.setTime(new DateBuilder()
                .setDate(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]))
                .setTime(Integer.parseInt(time[0]), Integer.parseInt(time[1]), Integer.parseInt(time[2]))
                .getDate());

        Double latitude = parseDouble(values[4]);
        Double longitude = parseDouble(values[5]);
        if (latitude != null && longitude != null) {
            position.setValid(true);
            position.setLatitude(latitude);
            position.setLongitude(longitude);
        } else {
            getLastLocation(position, position.getDeviceTime());
        }

        Double speed = parseDouble(values[6]);
        if (speed != null) {
            position.setSpeed(convertSpeed(speed, "kmh"));
        }
        Double course = parseDouble(values[7]);
        if (course != null) {
            position.setCourse(course);
        }

        Integer input = parseInteger(values[8]);
        if (input != null) {
            decodeInput(position, input);
        }

        Long odometer = parseLong(values[9]);
        if (odometer != null) {
            position.set(Position.KEY_OBD_ODOMETER, odometer * 1000);
        }
        setOptional(position, Position.KEY_RPM, parseInteger(values[10]));
        setOptional(position, Position.KEY_FUEL_USED, parseDouble(values[11]));
        setOptional(position, Position.KEY_FUEL_LEVEL, parseDouble(values[12]));
        setOptional(position, Position.KEY_COOLANT_TEMP, parseInteger(values[13]));

        Integer events = parseInteger(values[14]);
        if (events != null) {
            decodeEvents(position, events);
        }
        setOptional(position, Position.KEY_DRIVER_UNIQUE_ID, values[15].isEmpty() ? null : values[15]);

        if (values.length > 16) {
            Long gpsOdometer = parseLong(values[16]);
            if (gpsOdometer != null) {
                position.set(Position.KEY_ODOMETER, gpsOdometer * 1000);
            }
        }
        if (values.length > 17) {
            Double altitude = parseDouble(values[17]);
            if (altitude != null) {
                position.setAltitude(altitude);
            }
        }
        if (values.length > 18) {
            setOptional(position, Position.KEY_SATELLITES, parseInteger(values[18]));
        }
        for (int i = 19; i < values.length; i++) {
            if (!values[i].isEmpty()) {
                position.set("data" + (i - 18), values[i]);
            }
        }

        return position;
    }

    @Override
    protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        String sentence = ((String) msg).trim();
        if (sentence.startsWith(LOGIN_PREFIX)) {
            return decodeLogin(channel, remoteAddress, sentence);
        } else if (sentence.startsWith("$")) {
            return decodeData(channel, remoteAddress, sentence);
        }

        return null;
    }

}
