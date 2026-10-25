package ru.avangard.qa.ibmobile.core;

import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfigurationStrategy;

import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

/** Потоки JUnit = threads контура: на test и prod 1, там одна сессия на логин. */
public class Threads implements ParallelExecutionConfigurationStrategy {

    @Override
    public ParallelExecutionConfiguration createConfiguration(ConfigurationParameters parameters) {
        int n = Integer.parseInt(Config.get("threads"));
        return new ParallelExecutionConfiguration() {
            public int getParallelism() { return n; }
            public int getMinimumRunnable() { return n; }
            public int getMaxPoolSize() { return n; }
            public int getCorePoolSize() { return n; }
            public int getKeepAliveSeconds() { return 30; }
            public Predicate<? super ForkJoinPool> getSaturatePredicate() { return pool -> true; }
        };
    }
}
