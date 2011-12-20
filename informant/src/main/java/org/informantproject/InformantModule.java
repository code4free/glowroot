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
package org.informantproject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.informantproject.metrics.MetricBoss;
import org.informantproject.trace.StackSamplingBoss;
import org.informantproject.trace.StuckTraceBoss;
import org.informantproject.util.Clock;
import org.informantproject.util.DaemonExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Primary Guice module.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
class InformantModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(InformantModule.class);

    private final CommandLineOptions commandLineOptions;

    InformantModule(CommandLineOptions commandLineOptions) {
        this.commandLineOptions = commandLineOptions;
    }

    public static void start(Injector injector) {
        logger.debug("start()");
        injector.getInstance(StuckTraceBoss.class);
        injector.getInstance(StackSamplingBoss.class);
        injector.getInstance(MetricBoss.class);
        LocalModule.start(injector);
    }

    public static void shutdown(Injector injector) {
        logger.debug("shutdown()");
        LocalModule.shutdown(injector);
        injector.getInstance(StuckTraceBoss.class).shutdown();
        injector.getInstance(StackSamplingBoss.class).shutdown();
        injector.getInstance(MetricBoss.class).shutdown();
        injector.getInstance(ExecutorService.class).shutdownNow();
        injector.getInstance(ScheduledExecutorService.class).shutdownNow();
        try {
            injector.getInstance(Connection.class).close();
        } catch (SQLException e) {
            // warning only since it occurs during shutdown anyways
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    protected void configure() {
        logger.debug("configure()");
        install(new LocalModule(commandLineOptions.getUiPort()));
    }

    @Provides
    @Singleton
    protected Connection providesConnection() {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        try {
            return DriverManager.getConnection("jdbc:h2:" + commandLineOptions.getDbFile(), "sa",
                    "");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Provides
    @Singleton
    protected static Clock providesClock() {
        return Clock.systemClock();
    }

    @Provides
    @Singleton
    protected static Ticker providesTicker() {
        return Ticker.systemTicker();
    }

    @Provides
    @Singleton
    protected static ExecutorService providesExecutorService() {
        // TODO think about restricting the pool
        return DaemonExecutors.newCachedThreadPool("Informant-ExecutorPool");
    }

    @Provides
    @Singleton
    protected static ScheduledExecutorService providesScheduledExecutorService() {
        // TODO think about the pool size
        return DaemonExecutors.newScheduledThreadPool(10, "Informant-ScheduledExecutorPool");
    }
}