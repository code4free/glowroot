/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.local.metrics;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.informantproject.metrics.MetricValue;
import org.informantproject.util.Clock;
import org.informantproject.util.ConnectionTestProvider;
import org.informantproject.util.JdbcHelper;
import org.informantproject.util.MockClock;
import org.informantproject.util.ThreadChecker;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@RunWith(JukitoRunner.class)
public class MetricPointDaoTest {

    private Set<Thread> preExistingThreads;

    public static class Module extends JukitoModule {
        @Override
        protected void configureTest() {
            bind(Connection.class).toProvider(ConnectionTestProvider.class).in(TestSingleton.class);
            bind(MockClock.class).in(TestSingleton.class);
            bind(Clock.class).to(MockClock.class);
        }
    }

    @Before
    public void before(JdbcHelper jdbcHelper, Connection connection) throws SQLException {
        preExistingThreads = ThreadChecker.currentThreadList();
        if (jdbcHelper.tableExists("metric_point")) {
            Statement statement = connection.createStatement();
            statement.execute("drop table metric_point");
            statement.close();
        }
    }

    @After
    public void after(Connection connection) throws Exception {
        ThreadChecker.preShutdownNonDaemonThreadCheck(preExistingThreads);
        connection.close();
        ThreadChecker.postShutdownThreadCheck(preExistingThreads);
    }

    @Test
    public void shouldReadEmptyMetricPoints(MetricPointDao metricPointDao) {
        // given
        List<String> metricIds = Arrays.asList("cpu", "mem", "io");
        // when
        Map<String, List<Point>> metricPoints = metricPointDao.readMetricPoints(metricIds, 0,
                System.currentTimeMillis());
        // then
        assertThat(metricPoints.size(), is(0));
    }

    @Test
    public void shouldStoreMetricPoints(MetricPointDao metricPointDao, MockClock clock) {
        // given
        MetricValue mv1 = new MetricValue("cpu", 0.5);
        MetricValue mv2 = new MetricValue("mem", 10);
        MetricValue mv3 = new MetricValue("cpu", 0.75);
        // when
        long start = clock.updateTime();
        metricPointDao.storeMetricValues(Arrays.asList(mv1, mv2));
        clock.forwardTime(1000);
        metricPointDao.storeMetricValue(mv3);
        // then
        Map<String, List<Point>> storedMetricPoints = metricPointDao.readMetricPoints(
                Arrays.asList("cpu", "mem"), start, start + 1000);
        assertThat(storedMetricPoints.get("cpu"),
                is(Arrays.asList(new Point(start, 0.5), new Point(start + 1000, 0.75))));
        assertThat(storedMetricPoints.get("mem"),
                is(Collections.singletonList(new Point(start, 10))));
        assertThat(storedMetricPoints.size(), is(2));
    }

    @Test
    public void shouldOnlyReadMetricPointsInGivenTimeBand(MetricPointDao metricPointDao,
            MockClock clock) {

        // given
        MetricValue mv1 = new MetricValue("cpu", 0.5);
        MetricValue mv2 = new MetricValue("cpu", 0.75);
        MetricValue mv3 = new MetricValue("mem", 10);
        MetricValue mv4 = new MetricValue("mem", 11);
        long start = clock.updateTime();
        metricPointDao.storeMetricValue(mv1);
        clock.forwardTime(1);
        metricPointDao.storeMetricValue(mv2);
        clock.forwardTime(1000);
        metricPointDao.storeMetricValue(mv3);
        clock.forwardTime(1);
        metricPointDao.storeMetricValue(mv4);
        // when
        Map<String, List<Point>> storedMetricPoints = metricPointDao.readMetricPoints(
                Arrays.asList("cpu", "mem"), start + 1, start + 1001);
        // then
        assertThat(storedMetricPoints.size(), is(2));
        assertThat(storedMetricPoints.get("cpu"),
                is(Collections.singletonList(new Point(start + 1, 0.75))));
        assertThat(storedMetricPoints.get("mem"),
                is(Collections.singletonList(new Point(start + 1001, 10))));
    }

    @Test
    public void shouldOnlyReadMetricPointsForGivenMetricIds(MetricPointDao metricPointDao,
            MockClock clock) {

        // given
        MetricValue mv1 = new MetricValue("cpu", 0.5);
        MetricValue mv2 = new MetricValue("mem", 0.75);
        MetricValue mv3 = new MetricValue("io", 0.5);
        MetricValue mv4 = new MetricValue("net", 0.5);
        // when
        long now = clock.updateTime();
        metricPointDao.storeMetricValues(Arrays.asList(mv1, mv2, mv3, mv4));
        // then
        Map<String, List<Point>> storedMetricPoints = metricPointDao.readMetricPoints(
                Arrays.asList("io", "net"), now, now);
        assertThat(storedMetricPoints.size(), is(2));
        assertThat(storedMetricPoints.get("io"),
                is(Collections.singletonList(new Point(now, 0.5))));
        assertThat(storedMetricPoints.get("net"),
                is(Collections.singletonList(new Point(now, 0.5))));
    }
}
