package tests.core.threading;

import core.threading.ThreadManager;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadManagerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== ThreadManager Test Suite ===");
        System.out.println();

        testTaskExecution();
        testMultipleTasks();
        testInvalidThreadCount();
        testNullTask();
        testShutdown();

        System.out.println();
        System.out.println("=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private static void testTaskExecution() {

        AtomicInteger value = new AtomicInteger(0);

        try (ThreadManager manager = new ThreadManager(2)) {

            manager.submitTask(() -> value.incrementAndGet());

            manager.shutdown();
            manager.awaitCompletion(5);

            if (value.get() == 1) {
                pass("Single task executed successfully");
            } else {
                fail("Single task was not executed");
            }

        } catch (Exception e) {
            fail("Single task test threw exception: " + e.getMessage());
        }
    }

    private static void testMultipleTasks() {

        AtomicInteger counter = new AtomicInteger(0);

        try (ThreadManager manager = new ThreadManager(4)) {

            for (int i = 0; i < 10; i++) {
                manager.submitTask(counter::incrementAndGet);
            }

            manager.shutdown();
            manager.awaitCompletion(5);

            if (counter.get() == 10) {
                pass("Ten tasks executed successfully");
            } else {
                fail("Expected 10 completed tasks but got "
                        + counter.get());
            }

        } catch (Exception e) {
            fail("Multiple task test threw exception: "
                    + e.getMessage());
        }
    }

    private static void testInvalidThreadCount() {

        try {
            new ThreadManager(0);

            fail("Zero thread count should throw exception");

        } catch (IllegalArgumentException e) {

            pass("Zero thread count throws exception");
        }
    }

    private static void testNullTask() {

        try (ThreadManager manager = new ThreadManager(2)) {

            try {
                manager.submitTask(null);

                fail("Null task should throw exception");

            } catch (IllegalArgumentException e) {

                pass("Null task throws exception");
            }
        }
    }

    private static void testShutdown() {

        try (ThreadManager manager = new ThreadManager(2)) {

            manager.shutdown();

            if (manager.isShutdown()) {
                pass("ThreadManager shuts down correctly");
            } else {
                fail("ThreadManager did not shut down");
            }
        }
    }

    private static void pass(String message) {
        passed++;
        System.out.println("[PASS] " + message);
    }

    private static void fail(String message) {
        failed++;
        System.out.println("[FAIL] " + message);
    }
}
