package asyncj;

import akka.dispatch.ExecutionContexts;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import asyncj.AsyncResult;
import asyncj.AsyncUtils;
import asyncj.TaskScheduler;
import asyncj.impl.TaskExecutor;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Comparison benchmark with Akka library.
 * @author Roman Sakno
 * @version 1.1
 * @since 1.0
 */
public final class ComparisonBenchmarkWithScala {
    private static void pipeliningBenchmarkStep(final List<Long> scalaResults,
                                                final List<Long> asyncjResults) throws Exception {
        final TaskScheduler scheduler = TaskExecutor.newSingleThreadExecutor();
        final Integer[] source = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
        final int REPEAR_COUNT = 1000;
        final Mapper<Integer[], Integer[]> mapper = new Mapper<Integer[], Integer[]>() {
            @Override
            public Integer[] apply(final Integer[] array) {
                for (int j = 0; j < array.length; j++)
                    array[j] += j;
                return array;
            }
        };
        //execute pipelining via AsyncJ
        AsyncResult<Integer[]> ar = AsyncUtils.successful(scheduler, source);
        long currentNanos = System.nanoTime();
        for (int i = 0; i < REPEAR_COUNT; i++)
            ar = ar.then((Integer[] array)->{
                for (int j = 0; j < array.length; j++)
                    array[j] += j;
                return array;
            });
        ar.get(4, TimeUnit.SECONDS);
        //finish measurement
        final long asyncjResult = System.nanoTime() - currentNanos;
        asyncjResults.add(asyncjResult);
        final ExecutionContextExecutor executor = ExecutionContexts.fromExecutor(scheduler);
        //execute pipelining via Akka/Scala
        Future<Integer[]> ar2 = Futures.successful(source);
        currentNanos = System.nanoTime();
        for (int i = 0; i < REPEAR_COUNT; i++)
            ar2 = ar2.map(mapper, executor);
        Await.result(ar2, Duration.apply(5, TimeUnit.SECONDS));
        //finish measurement
        final long scalaResult = System.nanoTime() - currentNanos;
        scalaResults.add(scalaResult);
        switch (Long.compare(scalaResult, asyncjResult)){
            case -1: System.out.println(String.format("Akka/Scala is faster on %.2f percents",
                    100 * (1 - (double)scalaResult / asyncjResult)));
                break;
            case 0: System.out.println("Draw");break;
            case 1: System.out.println(String.format("AsyncJ is faster on %.2f percents",
                    100 * (1 - (double)asyncjResult / scalaResult)));
                break;
            default: System.out.println("Strange result");
        }
        System.out.println();
    }

    @Test
    public void pipeliningBenchmark() throws Exception {
        final int REPEAT_COUNT = 30;
        final List<Long> scalaResults = new ArrayList<>(REPEAT_COUNT);
        final List<Long> asyncjResults = new ArrayList<>(REPEAT_COUNT);
        for(int  i = 0; i < 30; i++) {
            pipeliningBenchmarkStep(scalaResults, asyncjResults);
            System.gc();
        }
        System.out.println("Scala/Akka result set:");
        scalaResults.stream().forEach(System.out::println);
        System.out.println("AsyncJ result set:");
        asyncjResults.stream().forEach(System.out::println);
    }
}
