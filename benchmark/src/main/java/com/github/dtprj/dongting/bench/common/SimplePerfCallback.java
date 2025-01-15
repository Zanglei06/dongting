/*
 * Copyright The Dongting Project
 *
 * The Dongting Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.dtprj.dongting.bench.common;

import com.github.dtprj.dongting.common.PerfCallback;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.Summary;

import java.lang.reflect.Field;
import java.util.SortedMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangli
 */
public abstract class SimplePerfCallback extends PerfCallback {
    protected static final DtLog log = DtLogs.getLogger(SimplePerfCallback.class);

    protected volatile boolean started = false;

    public SimplePerfCallback(boolean useNanos) {
        super(useNanos);
    }

    protected Summary createSummary(String name) {
        return Summary.build()
                .name(name)
                .help(name)
                .quantile(0.0, 0.0)
                .quantile(0.5, 0.02)
                .quantile(0.99, 0.003)
                .quantile(1.0, 0.0)
                .register();
    }

    public void start() {
        started = true;
    }

    protected void printTime(Summary summary) {
        Summary.Child.Value value = summary.get();
        if (value.count == 0) {
            return;
        }
        double avg = value.sum / value.count;
        SortedMap<Double, Double> q = value.quantiles;
        String s;
        if (useNanos) {
            s = String.format("%s: call %,.0f, avg %,.3fus, total %,.1fms, p50 %,.3fus, p99 %,.3fus, max %,.3fus, min %,.3fus",
                    getName(summary), value.count, avg / 1000, value.sum / 1_000_000, q.get(0.5) / 1000,
                    q.get(0.99) / 1000, q.get(1.0) / 1000, q.get(0.0) / 1000);
        } else {
            s = String.format("%s: call %,.0f, avg %,.1fms, total %,.1fms, p50 %,.1fms, p99 %,.1fms, max %,.1fms, min %,.1fms",
                    getName(summary), value.count, avg, value.sum, q.get(0.5), q.get(0.99), q.get(1.0), q.get(0.0));
        }
        log.info(s);
    }

    protected void printValue(Summary summary) {
        Summary.Child.Value value = summary.get();
        if (value.count == 0 || value.sum == 0) {
            return;
        }
        double avg = value.sum / value.count;
        SortedMap<Double, Double> q = value.quantiles;
        String s = String.format("%s: call %,.0f, avg %,.1f, total %,.1f, p50 %,.1f, p99 %,.1f, max %,.1f, min %,.1f",
                getName(summary), value.count, avg, value.sum, q.get(0.5), q.get(0.99), q.get(1.0), q.get(0.0));
        log.info(s);
    }

    protected void printCount(String name, LongAdder counter) {
        if (counter.sum() == 0) {
            return;
        }
        System.out.println(name + ": " + counter.sum());
    }

    private String getName(SimpleCollector<?> c) {
        try {
            Field f = c.getClass().getSuperclass().getDeclaredField("fullname");
            f.setAccessible(true);
            return (String) f.get(c);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
