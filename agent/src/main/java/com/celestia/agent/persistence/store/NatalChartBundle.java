package com.celestia.agent.persistence.store;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.judgement.SignificatorTable;

/**
 * A cached natal chart and the pure KP structures derived from it, plus the
 * {@code engine_version} string it was served with.
 */
public record NatalChartBundle(
        NatalChart chart,
        SignificatorTable significators,
        DashaTimeline dasha,
        String engineVersion) {}
