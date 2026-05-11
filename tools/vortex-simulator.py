#!/usr/bin/env python3

import argparse
import json
import math
import random
import socket
import time
from datetime import datetime, timezone


LOGIN_PREFIX = "LICORNE.VORTEX."


ROUTE = [
    (36.753768, 3.058756),
    (36.753137, 3.051365),
    (36.752347, 3.043276),
    (36.750783, 3.034114),
    (36.748276, 3.025531),
    (36.746364, 3.017618),
    (36.746882, 3.009748),
    (36.749912, 2.998375),
    (36.755021, 2.990749),
    (36.761409, 2.986957),
]


def haversine_meters(start, end):
    radius = 6371000
    lat1 = math.radians(start[0])
    lat2 = math.radians(end[0])
    delta_lat = math.radians(end[0] - start[0])
    delta_lon = math.radians(end[1] - start[1])
    a = math.sin(delta_lat / 2) ** 2
    a += math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def bearing_degrees(start, end):
    lat1 = math.radians(start[0])
    lat2 = math.radians(end[0])
    delta_lon = math.radians(end[1] - start[1])
    y = math.sin(delta_lon) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2)
    x -= math.sin(lat1) * math.cos(lat2) * math.cos(delta_lon)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def interpolate_route(route, steps_per_segment):
    points = []
    for index in range(len(route) - 1):
        start = route[index]
        end = route[index + 1]
        for step in range(steps_per_segment):
            ratio = step / steps_per_segment
            latitude = start[0] + (end[0] - start[0]) * ratio
            longitude = start[1] + (end[1] - start[1]) * ratio
            points.append((latitude, longitude))
    points.append(route[-1])
    return points


def event_value(index, speed, speed_limit, towing_start, towing_end):
    event = 0
    if index == 0:
        event |= 1 << 2
    if speed >= speed_limit:
        event |= 1 << 0
    if index == towing_start:
        event |= 1 << 4
    if index == towing_end:
        event |= 1 << 5
    return event


def input_value(index, towing_start, towing_end):
    value = 1 << 7
    if index % 9 in (3, 4):
        value |= 1 << 0
    if index % 17 == 8:
        value |= 1 << 1
    if towing_start <= index < towing_end:
        value |= 1 << 5
    return value


def build_login(args):
    return LOGIN_PREFIX + json.dumps({
        "IMEI": args.imei,
        "CCID": args.ccid,
        "Firmware": args.firmware,
        "Config": args.config_version,
        "CarModel": args.car_model,
        "CarModelVersion": args.car_model_version,
        "VIN": args.vin,
    }, separators=(",", ":"))


def build_position(args, index, point, next_point, distance_km):
    now = datetime.now(timezone.utc)
    base_speed = args.speed + 12 * math.sin(index / 4)
    speed = max(0, base_speed + random.uniform(-4, 4))
    heading = bearing_degrees(point, next_point)
    vehicle_odometer = args.odometer + distance_km
    gps_odometer = args.gps_odometer + distance_km
    fuel_used = args.fuel_used + distance_km * args.fuel_rate
    fuel_level = max(0, args.fuel_level - distance_km * 0.04)
    rpm = int(800 + speed * 38 + random.uniform(-120, 120))
    coolant_temp = int(78 + 8 * math.sin(index / 6) + random.uniform(-2, 2))
    satellites = random.randint(9, 14)
    altitude = int(args.altitude + 15 * math.sin(index / 8))
    towing_start = max(3, args.count // 3)
    towing_end = max(towing_start + 1, args.count // 3 + 3)
    inputs = input_value(index, towing_start, towing_end)
    events = event_value(index, speed, args.speed_limit, towing_start, towing_end)

    return "$" + ",".join([
        f"{now.year:04d}",
        f"{now.month:02d}",
        f"{now.day:02d}",
        f"{now.hour:02d}:{now.minute:02d}:{now.second:02d}",
        f"{point[0]:.6f}",
        f"{point[1]:.6f}",
        f"{speed:.0f}",
        f"{heading:.0f}",
        str(inputs),
        f"{vehicle_odometer:.0f}",
        str(rpm),
        f"{fuel_used:.0f}",
        f"{fuel_level:.0f}",
        str(coolant_temp),
        str(events),
        args.driver_id,
        f"{gps_odometer:.0f}",
        str(altitude),
        str(satellites),
        "",
    ])


def send_line(sock, line, dry_run):
    print(line, flush=True)
    if not dry_run:
        sock.sendall((line + "\r\n").encode("ascii"))


def run(args):
    points = interpolate_route(ROUTE, args.steps_per_segment)
    if args.count <= 0:
        args.count = len(points)

    sock = None
    if not args.dry_run:
        sock = socket.create_connection((args.host, args.port), timeout=args.timeout)
        sock.settimeout(args.timeout)

    try:
        send_line(sock, build_login(args), args.dry_run)
        time.sleep(args.login_delay)

        distance_km = 0
        previous = points[0]
        for index in range(args.count):
            point = points[index % len(points)]
            next_point = points[(index + 1) % len(points)]
            if index > 0:
                distance_km += haversine_meters(previous, point) / 1000
            previous = point
            send_line(sock, build_position(args, index, point, next_point, distance_km), args.dry_run)
            time.sleep(args.interval)
    finally:
        if sock is not None:
            sock.close()


def main():
    parser = argparse.ArgumentParser(description="VORTEX-G1 Traccar protocol simulator")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5264)
    parser.add_argument("--imei", default="012345678912345")
    parser.add_argument("--ccid", default="9999999999999999999")
    parser.add_argument("--firmware", default="1.0.0")
    parser.add_argument("--config-version", default="1.1.0")
    parser.add_argument("--car-model", default="Renault-Clio-2019")
    parser.add_argument("--car-model-version", default="1.2")
    parser.add_argument("--vin", default="V1234567AKD12345")
    parser.add_argument("--driver-id", default="460000151ED9001")
    parser.add_argument("--count", type=int, default=80)
    parser.add_argument("--interval", type=float, default=2.0)
    parser.add_argument("--login-delay", type=float, default=0.5)
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--speed", type=float, default=42.0)
    parser.add_argument("--speed-limit", type=float, default=50.0)
    parser.add_argument("--odometer", type=float, default=207809.0)
    parser.add_argument("--gps-odometer", type=float, default=533.0)
    parser.add_argument("--fuel-used", type=float, default=108457.0)
    parser.add_argument("--fuel-rate", type=float, default=0.08)
    parser.add_argument("--fuel-level", type=float, default=29.0)
    parser.add_argument("--altitude", type=float, default=1047.0)
    parser.add_argument("--steps-per-segment", type=int, default=8)
    parser.add_argument("--dry-run", action="store_true")
    run(parser.parse_args())


if __name__ == "__main__":
    main()
