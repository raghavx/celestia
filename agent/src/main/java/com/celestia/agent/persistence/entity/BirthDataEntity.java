package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** {@code birth_data} — one per user; the SPEC-007 {@code ResolvedBirth} persisted. */
@Entity
@Table(name = "birth_data")
public class BirthDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(length = 120)
    private String name;

    @Column(name = "place_query", nullable = false, length = 255)
    private String placeQuery;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "birth_time")
    private LocalTime birthTime;

    @Column(name = "birth_time_known", nullable = false)
    private boolean birthTimeKnown;

    @Column(name = "place_name", nullable = false, length = 255)
    private String placeName;

    @Column(nullable = false, length = 120)
    private String country = "";

    @Column(nullable = false, precision = 9, scale = 6)
    private java.math.BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private java.math.BigDecimal lon;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Column(name = "birth_utc", nullable = false)
    private Instant birthUtc;

    @Column(name = "offset_applied", nullable = false, length = 16)
    private String offsetApplied;

    @Column(name = "geocode_source", nullable = false, length = 32)
    private String geocodeSource;

    @Column(name = "stated_zone_id", length = 64)
    private String statedZoneId;

    @Column(nullable = false, length = 255)
    private String flags = "";

    @Column(name = "tzdb_version", nullable = false, length = 32)
    private String tzdbVersion;

    @Column(name = "tz_boundary_version", nullable = false, length = 32)
    private String tzBoundaryVersion;

    public BirthDataEntity() {}

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlaceQuery() {
        return placeQuery;
    }

    public void setPlaceQuery(String placeQuery) {
        this.placeQuery = placeQuery;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public LocalTime getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(LocalTime birthTime) {
        this.birthTime = birthTime;
    }

    public boolean isBirthTimeKnown() {
        return birthTimeKnown;
    }

    public void setBirthTimeKnown(boolean birthTimeKnown) {
        this.birthTimeKnown = birthTimeKnown;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public java.math.BigDecimal getLat() {
        return lat;
    }

    public void setLat(java.math.BigDecimal lat) {
        this.lat = lat;
    }

    public java.math.BigDecimal getLon() {
        return lon;
    }

    public void setLon(java.math.BigDecimal lon) {
        this.lon = lon;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public Instant getBirthUtc() {
        return birthUtc;
    }

    public void setBirthUtc(Instant birthUtc) {
        this.birthUtc = birthUtc;
    }

    public String getOffsetApplied() {
        return offsetApplied;
    }

    public void setOffsetApplied(String offsetApplied) {
        this.offsetApplied = offsetApplied;
    }

    public String getGeocodeSource() {
        return geocodeSource;
    }

    public void setGeocodeSource(String geocodeSource) {
        this.geocodeSource = geocodeSource;
    }

    public String getStatedZoneId() {
        return statedZoneId;
    }

    public void setStatedZoneId(String statedZoneId) {
        this.statedZoneId = statedZoneId;
    }

    public String getFlags() {
        return flags;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }

    public String getTzdbVersion() {
        return tzdbVersion;
    }

    public void setTzdbVersion(String tzdbVersion) {
        this.tzdbVersion = tzdbVersion;
    }

    public String getTzBoundaryVersion() {
        return tzBoundaryVersion;
    }

    public void setTzBoundaryVersion(String tzBoundaryVersion) {
        this.tzBoundaryVersion = tzBoundaryVersion;
    }
}
