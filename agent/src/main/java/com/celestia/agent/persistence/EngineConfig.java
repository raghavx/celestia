package com.celestia.agent.persistence;

import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.dasha.DashaTimelineFactory;
import com.celestia.core.horary.HoraryChartFactory;
import com.celestia.core.prediction.DailyPredictionFactory;
import com.celestia.ephemeris.HoraryHouseProvider;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.SunriseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The first place the pure KP engine is wired into Spring. {@code ChartCacheService}
 * cannot compute a chart without these; nothing here is stateful.
 *
 * <p>The {@link SwissEphemerisConfig} is resolved exactly as the golden-chart
 * test suite resolves it ({@code SwissEphemerisConfig.resolve()}) so a cached
 * chart matches the gated values (SPEC-008 {@code ChartCacheGoldenIT}).
 */
@Configuration(proxyBeanMethods = false)
public class EngineConfig {

    @Bean
    SwissEphemerisConfig swissEphemerisConfig() {
        return SwissEphemerisConfig.resolve();
    }

    @Bean
    PositionProvider positionProvider(SwissEphemerisConfig config) {
        return new SwissEphemerisPositionProvider(config);
    }

    @Bean
    HouseProvider houseProvider(SwissEphemerisConfig config) {
        return new SwissEphemerisHouseProvider(config);
    }

    @Bean
    HoraryHouseProvider horaryHouseProvider(SwissEphemerisConfig config) {
        return new SwissEphemerisHoraryHouseProvider(config);
    }

    @Bean
    SunriseProvider sunriseProvider(SwissEphemerisConfig config) {
        return new SwissEphemerisSunriseProvider(config);
    }

    @Bean
    NatalChartFactory natalChartFactory(PositionProvider positions, HouseProvider houses) {
        return new NatalChartFactory(positions, houses);
    }

    @Bean
    HoraryChartFactory horaryChartFactory(PositionProvider positions, HoraryHouseProvider houses) {
        return new HoraryChartFactory(positions, houses);
    }

    @Bean
    DashaTimelineFactory dashaTimelineFactory(PositionProvider positions) {
        return new DashaTimelineFactory(positions);
    }

    @Bean
    DailyPredictionFactory dailyPredictionFactory(PositionProvider positions) {
        return new DailyPredictionFactory(positions);
    }
}
