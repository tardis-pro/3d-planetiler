package com.onthegomap.flatmap.benchmarks;

import static io.prometheus.client.Collector.NANOSECONDS_PER_SECOND;

import com.onthegomap.flatmap.collection.LongLongMap;
import com.onthegomap.flatmap.stats.Counter;
import com.onthegomap.flatmap.stats.ProcessInfo;
import com.onthegomap.flatmap.stats.ProgressLoggers;
import com.onthegomap.flatmap.stats.Stats;
import com.onthegomap.flatmap.util.FileUtils;
import com.onthegomap.flatmap.util.Format;
import com.onthegomap.flatmap.worker.Worker;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class LongLongMapBench {

  public static void main(String[] args) throws InterruptedException {
    Path path = Path.of("./llmaptest");
    FileUtils.delete(path);
    LongLongMap map = switch (args[0]) {
      case "sparsemem2" -> LongLongMap.newInMemorySparseArray2();
      case "sparsearraymemory" -> LongLongMap.newInMemorySparseArray();
      case "hppc" -> new LongLongMap.HppcMap();
      case "array" -> new LongLongMap.Array();

      case "sparse2" -> LongLongMap.newFileBackedSparseArray2(path);
      case "sqlite" -> LongLongMap.newSqlite(path);
      case "sparsearray" -> LongLongMap.newFileBackedSparseArray(path);
      case "mapdb" -> LongLongMap.newFileBackedSortedTable(path);
      default -> throw new IllegalStateException("Unexpected value: " + args[0]);
    };
    long entries = Long.parseLong(args[1]);
    int readers = Integer.parseInt(args[2]);

    class LocalCounter {

      long count = 0;
    }
    LocalCounter counter = new LocalCounter();
    ProgressLoggers loggers = new ProgressLoggers("write")
      .addRatePercentCounter("entries", entries, () -> counter.count)
      .newLine()
      .addProcessStats();
    AtomicReference<String> writeRate = new AtomicReference<>();
    new Worker("writer", Stats.inMemory(), 1, () -> {
      long start = System.nanoTime();
      for (long i = 0; i < entries; i++) {
        map.put(i + 1L, i + 2L);
        counter.count = i;
      }
      long end = System.nanoTime();
      String rate = Format.formatNumeric(entries * NANOSECONDS_PER_SECOND / (end - start), false) + "/s";
      System.err.println("Loaded " + entries + " in " + Duration.ofNanos(end - start).toSeconds() + "s (" + rate + ")");
      writeRate.set(rate);
    }).awaitAndLog(loggers, Duration.ofSeconds(10));

    map.get(1);
    System.err.println("Storage: " + Format.formatStorage(map.fileSize(), false));

    Counter.Readable readCount = Counter.newMultiThreadCounter();
    loggers = new ProgressLoggers("read")
      .addRateCounter("entries", readCount)
      .newLine()
      .addProcessStats();
    CountDownLatch latch = new CountDownLatch(readers);
    for (int i = 0; i < readers; i++) {
      int rnum = i;
      new Thread(() -> {
        latch.countDown();
        try {
          latch.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        Random random = new Random(rnum);
        try {
          long sum = 0;
          long b = 0;
          while (b == 0) {
            readCount.inc();
            long key = 1L + (Math.abs(random.nextLong()) % entries);
            long value = map.get(key);
            assert key + 1 == value : key + " value was " + value;
            sum += value;
          }
          System.err.println(sum);
        } catch (Throwable e) {
          e.printStackTrace();
          System.exit(1);
        }
      }).start();
    }
    latch.await();
    long start = System.nanoTime();
    for (int i = 0; i < 3; i++) {
      Thread.sleep(10000);
      loggers.log();
    }
    long end = System.nanoTime();
    long read = readCount.getAsLong();
    String readRate = Format.formatNumeric(read * NANOSECONDS_PER_SECOND / (end - start), false) + "/s";
    System.err.println("Read " + read + " in 30s (" + readRate + ")");
    System.err.println(
      String.join("\t",
        args[0],
        args[1],
        args[2],
        args[3],
        Format.formatStorage(ProcessInfo.getMaxMemoryBytes(), false),
        Format.formatStorage(map.fileSize(), false),
        Format.formatStorage(FileUtils.size(path), false),
        writeRate.get(),
        readRate
      )
    );
    Thread.sleep(100);
    System.exit(0);
  }
}
