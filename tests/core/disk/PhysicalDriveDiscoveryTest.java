package tests.core.disk;

import core.disk.PhysicalDriveDiscovery.PhysicalDrive;

public class PhysicalDriveDiscoveryTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== PhysicalDriveDiscovery Test Suite ===\n");

        testEligibleDrive();
        testZeroCapacityRejected();
        testRootDriveRejected();
        testMountedDriveRejected();
        testReadOnlyDriveRejected();
        testMultipleSafetyConditions();

        System.out.println(
                "\n=== Results: " + passed + " passed, "
                        + failed + " failed ==="
        );

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testEligibleDrive() {
        PhysicalDrive drive = drive(
                16_000_000_000L, false, false, false, false
        );
        check("Unmounted, writable, non-system drive is eligible",
                drive.isSelectable());
    }

    private static void testZeroCapacityRejected() {
        PhysicalDrive drive = drive(
                0, false, false, false, false
        );
        check("Zero-capacity drive is rejected", !drive.isSelectable());
    }

    private static void testRootDriveRejected() {
        PhysicalDrive drive = drive(
                16_000_000_000L, true, false, false, false
        );
        check("System/root drive is rejected", !drive.isSelectable());
    }

    private static void testMountedDriveRejected() {
        PhysicalDrive drive = drive(
                16_000_000_000L, false, true, false, false
        );
        check("Drive with mounted filesystem is rejected",
                !drive.isSelectable());
    }

    private static void testReadOnlyDriveRejected() {
        PhysicalDrive drive = drive(
                16_000_000_000L, false, false, true, false
        );
        check("Read-only drive is rejected", !drive.isSelectable());
    }

    private static void testMultipleSafetyConditions() {
        PhysicalDrive drive = drive(
                0, true, true, true, false
        );
        check("Multiple unsafe conditions are rejected",
                !drive.isSelectable());
    }

    private static PhysicalDrive drive(
            long size,
            boolean root,
            boolean mounted,
            boolean readOnly,
            boolean removable
    ) {
        return new PhysicalDrive(
                "test-disk",
                "/dev/test-disk",
                size,
                "test model",
                "usb",
                removable,
                readOnly,
                root,
                mounted
        );
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + name);
            passed++;
        } else {
            System.out.println("  [FAIL] " + name);
            failed++;
        }
    }
}
