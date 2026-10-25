package ru.avangard.qa.ibmobile.core;

import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfigurationStrategy;

import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

/** Число потоков JUnit = threads из env/&lt;env&gt;.properties (на test и prod — 1, там одна сессия на логин). */
public class Threads implements ParallelExecutionConfigurationStrategy {

    @Override
    public ParallelExecutionConfiguration createConfiguration(ConfigurationParameters parameters) {
        int threads = Config.threads();
        return new ParallelExecutionConfiguration() {
            @Override
            public int getParallelism() {
                return threads;
            }

            @Override
            public int getMinimumRunnable() {
                return threads;
            }

            @Override
            public int getMaxPoolSize() {
                return threads;
            }

            @Override
            public int getCorePoolSize() {
                return threads;
            }

            @Override
            public int getKeepAliveSeconds() {
                return 30;
            }

            @Override
            public Predicate<? super ForkJoinPool> getSaturatePredicate() {
                return pool -> true;
            }
        };
    }
}
