package com.example.siteplatform.project.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectCoordinateConverterTest {

    @Test
    void gcj02CoordinatesAreNormalizedWithoutOffset() {
        var coordinate = ProjectCoordinateConverter.toGcj02(
                new BigDecimal("121.5000004"), new BigDecimal("31.2000004"), "gcj02").orElseThrow();

        assertThat(coordinate.longitude()).isEqualByComparingTo("121.500000");
        assertThat(coordinate.latitude()).isEqualByComparingTo("31.200000");
    }

    @Test
    void bd09CoordinatesAreConvertedToGcj02() {
        var coordinate = ProjectCoordinateConverter.toGcj02(
                new BigDecimal("116.41036949371029"),
                new BigDecimal("39.92133699351021"), "BD09").orElseThrow();

        assertThat(coordinate.longitude()).isEqualByComparingTo("116.404000");
        assertThat(coordinate.latitude()).isEqualByComparingTo("39.915000");
    }

    @Test
    void knownShanghaiBd09CoordinateMatchesExpectedGcj02Point() {
        var coordinate = ProjectCoordinateConverter.toGcj02(
                new BigDecimal("121.480237"), new BigDecimal("31.236305"), "BD09").orElseThrow();

        assertThat(coordinate.longitude()).isEqualByComparingTo("121.473699");
        assertThat(coordinate.latitude()).isEqualByComparingTo("31.230371");
    }

    @Test
    void wgs84CoordinatesAreConvertedToGcj02InsideChina() {
        var coordinate = ProjectCoordinateConverter.toGcj02(
                new BigDecimal("116.397128"), new BigDecimal("39.916527"), "WGS84").orElseThrow();

        assertThat(coordinate.longitude()).isEqualByComparingTo("116.403372");
        assertThat(coordinate.latitude()).isEqualByComparingTo("39.917931");
    }

    @Test
    void wgs84CoordinatesOutsideChinaRemainUnchanged() {
        var coordinate = ProjectCoordinateConverter.toGcj02(
                new BigDecimal("2.352222"), new BigDecimal("48.856614"), "WGS84").orElseThrow();

        assertThat(coordinate.longitude()).isEqualByComparingTo("2.352222");
        assertThat(coordinate.latitude()).isEqualByComparingTo("48.856614");
    }

    @Test
    void incompleteOutOfRangeOrUnknownCoordinatesAreRejected() {
        assertThat(ProjectCoordinateConverter.toGcj02(null, BigDecimal.ZERO, "GCJ02")).isEmpty();
        assertThat(ProjectCoordinateConverter.toGcj02(new BigDecimal("181"), BigDecimal.ZERO, "GCJ02")).isEmpty();
        assertThat(ProjectCoordinateConverter.toGcj02(BigDecimal.ZERO, BigDecimal.ZERO, "UNKNOWN")).isEmpty();
    }
}
