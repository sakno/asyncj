package asyncj.benchmarks;

import akka.dispatch.ExecutionContexts;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import asyncj.AsyncResult;
import asyncj.AsyncUtils;
import asyncj.TaskScheduler;
import asyncj.impl.TaskExecutor;
import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

/**
 * Comparison benchmark with Akka library.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
@BenchmarkMethodChart(filePrefix = "asyncj-vs-scala")
public final class ComparisonBenchmarkWithScala extends AbstractBenchmark {
    private final Integer[] source = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
    private final Mapper<Integer[], Integer[]> mapper = new Mapper<Integer[], Integer[]>() {
        @Override
        public Integer[] apply(final Integer[] array) {
            for (int j = 0; j < array.length; j++)
                array[j] += j;
            return array;
        }
    };

    private static final int PIPELINE_LENGTH = 100;

    private void scalaPipeliningBenchmark(final ExecutionContextExecutor executor,
                                          final int pipelineLength) throws Exception {
        //execute pipelining via Akka/Scala
        Future<Integer[]> ar2 = Futures.successful(source);
        for (int i = 0; i < pipelineLength; i++)
            ar2 = ar2.map(mapper, executor);
        Await.result(ar2, Duration.apply(5, TimeUnit.SECONDS));
    }

    private void asyncjPipelineBenchmark(final TaskScheduler scheduler,
                                         final int pipelineLength) throws Exception {
        //execute pipelining via AsyncJ
        AsyncResult<Integer[]> ar = AsyncUtils.successful(scheduler, source);
        for (int i = 0; i < pipelineLength; i++)
            ar = ar.then((Integer[] array) -> {
                for (int j = 0; j < array.length; j++)
                    array[j] += j;
                return array;
            });
        ar.get(4, TimeUnit.SECONDS);
    }

    @Test
    @BenchmarkOptions(benchmarkRounds = 10, warmupRounds = 5)
    public void scalaPipelineBenchmark() throws Exception {
        final ExecutionContextExecutor executor = ExecutionContexts.fromExecutor(TaskExecutor.newDefaultThreadExecutor());
        scalaPipeliningBenchmark(executor, PIPELINE_LENGTH);
    }

    @Test
    @BenchmarkOptions(benchmarkRounds = 10, warmupRounds = 5)
    public void asyncjPipelineBenchmark() throws Exception {
        final TaskScheduler scheduler = TaskExecutor.newDefaultThreadExecutor();
        asyncjPipelineBenchmark(scheduler, PIPELINE_LENGTH);
    }
}
