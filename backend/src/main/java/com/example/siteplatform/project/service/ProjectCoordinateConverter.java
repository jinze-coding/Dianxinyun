package com.example.siteplatform.project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/** Converts stored project coordinates to the GCJ-02 coordinates required by WeChat maps. */
public final class ProjectCoordinateConverter {

    private static final double PI = Math.PI;
    private static final double BAIDU_PI = Math.PI * 3000.0 / 180.0;
    private static final double EARTH_SEMI_MAJOR_AXIS = 6378245.0;
    private static final double EARTH_ECCENTRICITY_SQUARED = 0.00669342162296594323;
    private static final int PUBLIC_COORDINATE_SCALE = 6;

    private ProjectCoordinateConverter() {
    }

    public static Optional<Coordinate> toGcj02(BigDecimal longitude,
                                                BigDecimal latitude,
                                                String coordinateType) {
        if (longitude == null || latitude == null || !inCoordinateRange(longitude, latitude)) {
            return Optional.empty();
        }
        String normalizedType = coordinateType == null
                ? "BD09"
                : coordinateType.trim().toUpperCase(Locale.ROOT);
        double sourceLongitude = longitude.doubleValue();
        double sourceLatitude = latitude.doubleValue();
        double[] converted = switch (normalizedType) {
            case "BD09" -> bd09ToGcj02(sourceLongitude, sourceLatitude);
            case "GCJ02" -> new double[]{sourceLongitude, sourceLatitude};
            case "WGS84" -> wgs84ToGcj02(sourceLongitude, sourceLatitude);
            default -> null;
        };
        if (converted == null || !Double.isFinite(converted[0]) || !Double.isFinite(converted[1])) {
            return Optional.empty();
        }
        return Optional.of(new Coordinate(decimal(converted[0]), decimal(converted[1])));
    }

    private static boolean inCoordinateRange(BigDecimal longitude, BigDecimal latitude) {
        return longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    private static double[] bd09ToGcj02(double longitude, double latitude) {
        double x = longitude - 0.0065;
        double y = latitude - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * BAIDU_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * BAIDU_PI);
        return new double[]{z * Math.cos(theta), z * Math.sin(theta)};
    }

    private static double[] wgs84ToGcj02(double longitude, double latitude) {
        if (outsideChina(longitude, latitude)) {
            return new double[]{longitude, latitude};
        }
        double latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0);
        double longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0);
        double latitudeRadians = latitude / 180.0 * PI;
        double sine = Math.sin(latitudeRadians);
        double magic = 1 - EARTH_ECCENTRICITY_SQUARED * sine * sine;
        double squareRootMagic = Math.sqrt(magic);
        latitudeOffset = latitudeOffset * 180.0
                / ((EARTH_SEMI_MAJOR_AXIS * (1 - EARTH_ECCENTRICITY_SQUARED))
                / (magic * squareRootMagic) * PI);
        longitudeOffset = longitudeOffset * 180.0
                / (EARTH_SEMI_MAJOR_AXIS / squareRootMagic * Math.cos(latitudeRadians) * PI);
        return new double[]{longitude + longitudeOffset, latitude + latitudeOffset};
    }

    private static boolean outsideChina(double longitude, double latitude) {
        return longitude < 72.004 || longitude > 137.8347
                || latitude < 0.8293 || latitude > 55.8271;
    }

    private static double transformLatitude(double x, double y) {
        double value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        value += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return value;
    }

    private static double transformLongitude(double x, double y) {
        double value = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        value += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return value;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(PUBLIC_COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    public record Coordinate(BigDecimal longitude, BigDecimal latitude) {
    }
}
