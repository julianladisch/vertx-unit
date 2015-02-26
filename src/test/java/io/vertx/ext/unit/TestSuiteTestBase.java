package io.vertx.ext.unit;

import io.vertx.core.Handler;
import io.vertx.ext.unit.impl.TestSuiteImpl;
import io.vertx.ext.unit.impl.TestSuiteRunner;
import io.vertx.ext.unit.report.TestResult;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public abstract class TestSuiteTestBase {

  protected Function<TestSuiteImpl, TestSuiteRunner> getRunner;
  protected Consumer<TestSuiteRunner> run;
  protected Consumer<Async> completeAsync;

  public TestSuiteTestBase() {
  }

  void run(TestSuite suite, TestReporter reporter) {
    run.accept(getRunner.apply((TestSuiteImpl) suite).handler(reporter));
  }

  void run(TestSuite suite, TestReporter reporter, long timeout) {
    run.accept(getRunner.apply((TestSuiteImpl) suite).handler(reporter).setTimeout(timeout));
  }

  protected boolean checkTest(TestContext test) {
    return true;
  }

  @Test
  public void runTest() {
    AtomicInteger count = new AtomicInteger();
    AtomicBoolean sameContext = new AtomicBoolean();
    TestSuite suite = TestSuite.create("my_suite").
        test("my_test", context -> {
          sameContext.set(checkTest(context));
          count.compareAndSet(0, 1);
        });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    reporter.await();
    assertTrue(sameContext.get());
    assertEquals(1, count.get());
    assertEquals(0, reporter.exceptions.size());
    assertEquals(1, reporter.results.size());
    TestResult result = reporter.results.get(0);
    assertEquals("my_test", result.name());
    assertTrue(result.succeeded());
    assertFalse(result.failed());
    assertNull(result.failure());
  }

  @Test
  public void runTestWithAsyncCompletion() throws Exception {
    BlockingQueue<Async> queue = new ArrayBlockingQueue<>(1);
    AtomicInteger count = new AtomicInteger();
    TestSuite suite = TestSuite.create("my_suite").
        test("my_test", context -> {
          count.compareAndSet(0, 1);
          queue.add(context.async());
        });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    Async async = queue.poll(2, TimeUnit.SECONDS);
    assertEquals(1, count.get());
    assertFalse(reporter.completed());
    completeAsync.accept(async);
    reporter.await();
    assertTrue(reporter.completed());
    assertEquals(0, reporter.exceptions.size());
    assertEquals(1, reporter.results.size());
    TestResult result = reporter.results.get(0);
    assertEquals("my_test", result.name());
    assertTrue(result.succeeded());
    assertFalse(result.failed());
    assertNull(result.failure());
  }

  @Test
  public void runTestWithAsyncCompletionCompletedInTest() throws Exception {
    AtomicBoolean ok = new AtomicBoolean();
    TestSuite suite = TestSuite.create("my_suite").
        test("my_test", context -> {
          Async async = context.async();
          async.complete();
          try {
            async.complete();
          } catch (IllegalStateException ignore) {
          }
          ok.set(true);
        });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    reporter.await();
    assertTrue(ok.get());
    assertTrue(reporter.completed());
    assertEquals(0, reporter.exceptions.size());
    assertEquals(1, reporter.results.size());
    TestResult result = reporter.results.get(0);
    assertEquals("my_test", result.name());
    assertTrue(result.succeeded());
    assertFalse(result.failed());
    assertNull(result.failure());
  }

  @Test
  public void runTestWithAsyncCompletionAfterFailureInTest() throws Exception {
    AtomicBoolean completed = new AtomicBoolean();
    TestSuite suite = TestSuite.create("my_suite").
        test("my_test", context -> {
          Async async = context.async();
          try {
            context.fail("msg");
          } catch (AssertionError ignore) {
          }
          async.complete();
          completed.set(true);
        });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    reporter.await();
    assertTrue(completed.get());
    assertTrue(reporter.completed());
    assertEquals(0, reporter.exceptions.size());
    assertEquals(1, reporter.results.size());
    TestResult result = reporter.results.get(0);
    assertEquals("my_test", result.name());
    assertTrue(result.failed());
  }

  @Test
  public void runTestWithAssertionError() {
    failTest(context -> context.fail("message_failure"));
  }

  @Test
  public void runTestWithEmptyRuntimeException() {
    failTest(context -> { throw new RuntimeException(); });
  }

  @Test
  public void runTestWithRuntimeException() {
    failTest(context -> { throw new RuntimeException("message_failure"); });
  }

  private void failTest(Handler<TestContext> thrower) {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    TestSuite suite = TestSuite.create("my_suite").
        test("my_test", context -> {
          try {
            thrower.handle(context);
          } catch (Error | RuntimeException e) {
            failure.set(e);
            throw e;
          }
        });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    reporter.await();
    assertEquals(0, reporter.exceptions.size());
    assertEquals(1, reporter.results.size());
    TestResult result = reporter.results.get(0);
    assertEquals("my_test", result.name());
    assertFalse(result.succeeded());
    assertTrue(result.failed());
    assertNotNull(result.failure());
    assertSame(failure.get().getMessage(), result.failure().message());
    assertSame(failure.get(), result.failure().cause());
  }

  @Test
  public void runTestWithAsyncFailure() throws Exception {
    BlockingQueue<TestContext> queue = new ArrayBlockingQueue<>(1);
    TestSuite suite = TestSuite.create("my_suite").test("my_test", context -> {
      context.async();
      queue.add(context);
    });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    assertFalse(reporter.completed());
    TestContext test = queue.poll(2, TimeUnit.SECONDS);
    try {
      test.fail("the_message");
    } catch (AssertionError ignore) {
    }
    reporter.await();
    assertTrue(reporter.completed());
  }

  @Test
  public void reportFailureAfterTestCompleted() {
    AtomicReference<TestContext> testRef = new AtomicReference<>();
    TestSuite suite = TestSuite.create("my_suite").test("my_test_1", testRef::set).test("my_test_2", context -> {
      try {
        testRef.get().fail();
      } catch (Exception e) {
      }
    });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    reporter.await();
    assertEquals(1, reporter.exceptions.size());
    assertEquals(2, reporter.results.size());
    TestResult result = reporter.results.get(0);
    assertEquals("my_test_1", result.name());
    assertTrue(result.succeeded());
    assertNull(result.failure());
    result = reporter.results.get(1);
    assertEquals("my_test_2", result.name());
    assertTrue(result.failed());
    assertNotNull(result.failure());
  }

  @Test
  public void runBefore() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean sameContext = new AtomicBoolean();
      int val = i;
      TestSuite suite = TestSuite.create("my_suite").
          test("my_test_1", context -> {
            sameContext.set(checkTest(context));
            count.compareAndSet(1, 2);
          }).test("my_test_2", context -> {
        if (val == 0) {
          count.compareAndSet(3, 4);
        } else {
          count.compareAndSet(2, 3);
        }
      });
      if (i == 0) {
        suite = suite.beforeEach(context -> count.incrementAndGet());
      } else {
        suite = suite.before(context -> count.incrementAndGet());
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      reporter.await();
      assertEquals(i == 0 ? 4 : 3, count.get());
      assertTrue(sameContext.get());
      assertEquals(0, reporter.exceptions.size());
      assertEquals(2, reporter.results.size());
      assertEquals("my_test_1", reporter.results.get(0).name());
      assertTrue(reporter.results.get(0).succeeded());
      assertEquals("my_test_2", reporter.results.get(1).name());
      assertTrue(reporter.results.get(1).succeeded());
    }
  }

  @Test
  public void runBeforeWithAsyncCompletion() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean sameContext = new AtomicBoolean();
      BlockingQueue<Async> queue = new ArrayBlockingQueue<>(1);
      TestSuite suite = TestSuite.create("my_suite").test("my_test", context -> {
        count.compareAndSet(1, 2);
        sameContext.set(checkTest(context));
      });
      if (i == 0) {
        suite = suite.before(context -> {
          count.compareAndSet(0, 1);
          queue.add(context.async());
        });
      } else {
        suite = suite.beforeEach(context -> {
          count.compareAndSet(0, 1);
          queue.add(context.async());
        });
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      Async async = queue.poll(2, TimeUnit.SECONDS);
      completeAsync.accept(async);
      reporter.await();
      assertEquals(2, count.get());
      assertTrue(sameContext.get());
      assertEquals(0, reporter.exceptions.size());
      assertEquals(1, reporter.results.size());
      assertEquals("my_test", reporter.results.get(0).name());
      assertTrue(reporter.results.get(0).succeeded());
    }
  }

  @Test
  public void failBefore() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      TestSuite suite = TestSuite.create("my_suite").
          test("my_test_1", context -> count.incrementAndGet()).
          test("my_test_2", context -> count.incrementAndGet());
      if (i == 0) {
        suite.before(context -> {
          throw new RuntimeException();
        });
      } else {
        AtomicBoolean failed = new AtomicBoolean();
        suite.beforeEach(context -> {
          if (failed.compareAndSet(false, true)) {
            throw new RuntimeException();
          }
        });
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      reporter.await();
      if (i == 0) {
        assertEquals(0, count.get());
        assertEquals(0, reporter.results.size());
        assertEquals(1, reporter.exceptions.size());
      } else {
        assertEquals(1, count.get());
        assertEquals(0, reporter.exceptions.size());
        assertEquals(2, reporter.results.size());
        assertEquals("my_test_1", reporter.results.get(0).name());
        assertTrue(reporter.results.get(0).failed());
        assertEquals("my_test_2", reporter.results.get(1).name());
        assertTrue(reporter.results.get(1).succeeded());
      }
    }
  }

  @Test
  public void runAfterWithAsyncCompletion() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      BlockingQueue<Async> queue = new ArrayBlockingQueue<>(1);
      TestSuite suite = TestSuite.create("my_suite").test("my_test", context -> {
        count.compareAndSet(0, 1);
      });
      if (i == 0) {
        suite = suite.after(context -> {
          count.compareAndSet(1, 2);
          queue.add(context.async());
        });
      } else {
        suite = suite.afterEach(context -> {
          count.compareAndSet(1, 2);
          queue.add(context.async());
        });
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      Async async = queue.poll(2, TimeUnit.SECONDS);
      assertFalse(reporter.completed());
      assertEquals(2, count.get());
      completeAsync.accept(async);
      reporter.await();
      assertEquals(0, reporter.exceptions.size());
      assertEquals(1, reporter.results.size());
      assertEquals("my_test", reporter.results.get(0).name());
      assertTrue(reporter.results.get(0).succeeded());
    }
  }

  @Test
  public void runAfter() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      AtomicBoolean sameContext = new AtomicBoolean();
      int val = i;
      TestSuite suite = TestSuite.create("my_suite").
          test("my_test_1", context -> {
            count.compareAndSet(0, 1);
          }).
          test("my_test_2", context -> {
            if (val == 0) {
              count.compareAndSet(1, 2);
            } else {
              count.compareAndSet(2, 3);
            }
          });
      if (i == 0) {
        suite = suite.after(context -> {
          sameContext.set(checkTest(context));
          count.incrementAndGet();
        });
      } else {
        suite = suite.afterEach(context -> {
          sameContext.set(checkTest(context));
          count.incrementAndGet();
        });
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      reporter.await();
      assertEquals(i == 0 ? 3 : 4, count.get());
      assertTrue(sameContext.get());
      assertEquals(0, reporter.exceptions.size());
      assertEquals(2, reporter.results.size());
      assertEquals("my_test_1", reporter.results.get(0).name());
      assertTrue(reporter.results.get(0).succeeded());
      assertEquals("my_test_2", reporter.results.get(1).name());
      assertTrue(reporter.results.get(1).succeeded());
    }
  }

  @Test
  public void afterIsRunAfterFailure() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      TestSuite suite = TestSuite.create("my_suite").test("my_test", context -> {
        count.compareAndSet(0, 1);
        context.fail("the_message");
      });
      if (i == 0) {
        suite = suite.after(context -> count.compareAndSet(1, 2));
      } else {
        suite = suite.afterEach(context -> count.compareAndSet(1, 2));
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      reporter.await();
      assertEquals(2, count.get());
      assertEquals(0, reporter.exceptions.size());
      assertEquals(1, reporter.results.size());
      assertEquals("my_test", reporter.results.get(0).name());
      assertTrue(reporter.results.get(0).failed());
      assertEquals("the_message", reporter.results.get(0).failure().message());
    }
  }

  @Test
  public void failAfter() throws Exception {
    for (int i = 0;i < 2;i++) {
      AtomicInteger count = new AtomicInteger();
      int val = i;
      TestSuite suite = TestSuite.create("my_suite").
          test("my_test_1", context -> count.compareAndSet(0, 1)).
          test("my_test_2", context -> {
            if (val == 0) {
              count.compareAndSet(1, 2);
            } else {
              count.compareAndSet(2, 3);
            }
          });
      if (i == 0) {
        suite = suite.after(context -> {
          count.incrementAndGet();
          context.fail("the_message");
        });
      } else {
        AtomicBoolean failed = new AtomicBoolean();
        suite = suite.afterEach(context -> {
          count.incrementAndGet();
          if (failed.compareAndSet(false, true)) {
            context.fail("the_message");
          }
        });
      }
      TestReporter reporter = new TestReporter();
      run(suite, reporter);
      reporter.await();
      if (i == 0) {
        assertEquals(3, count.get());
        assertEquals(1, reporter.exceptions.size());
        assertEquals(2, reporter.results.size());
        assertEquals("my_test_1", reporter.results.get(0).name());
        assertTrue(reporter.results.get(0).succeeded());
        assertEquals("my_test_2", reporter.results.get(1).name());
        assertTrue(reporter.results.get(1).succeeded());
      } else {
        assertEquals(4, count.get());
        assertEquals(0, reporter.exceptions.size());
        assertEquals(2, reporter.results.size());
        assertEquals("my_test_1", reporter.results.get(0).name());
        assertTrue(reporter.results.get(0).failed());
        assertEquals("my_test_2", reporter.results.get(1).name());
        assertTrue(reporter.results.get(1).succeeded());
      }
    }
  }

  @Test
  public void timeExecution() {
    TestSuite suite = TestSuite.create("my_suite").test("my_test", context -> {
      Async async = context.async();
      new Thread() {
        @Override
        public void run() {
          try {
            Thread.sleep(15);
          } catch (InterruptedException ignore) {
          } finally {
            async.complete();
          }
        }
      }.start();
    });
    TestReporter reporter = new TestReporter();
    run(suite, reporter);
    reporter.await();
    assertEquals(0, reporter.exceptions.size());
    assertEquals(1, reporter.results.size());
    assertEquals("my_test", reporter.results.get(0).name());
    assertFalse(reporter.results.get(0).failed());
    assertTrue(reporter.results.get(0).durationTime() >= 15);
  }

  @Test
  public void testTimeout() throws Exception {
    BlockingQueue<Async> queue = new ArrayBlockingQueue<>(2);
    TestSuite suite = TestSuite.create("my_suite").
        test("my_test0", context -> queue.add(context.async())).
        test("my_test1", context -> queue.add(context.async()));
    TestReporter reporter = new TestReporter();
    run(suite, reporter, 300); // 300 ms
    reporter.await(); // Wait until timeout and suite completes
    assertEquals(2, reporter.results.size());
    for (int i = 0;i < 2;i++) {
      Async async = queue.poll(2, TimeUnit.SECONDS);
      assertEquals("my_test" + i, reporter.results.get(i).name());
      assertTrue(reporter.results.get(i).failed());
      assertNotNull(reporter.results.get(i).failure());
      assertTrue(reporter.results.get(i).failure().cause() instanceof TimeoutException);
      async.complete();
    }
  }
}
