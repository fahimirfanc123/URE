package tests.gui;

import gui.report.RecoveryReport;
import gui.report.RecoveryReportItem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RecoveryReportTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== RecoveryReport Test Suite ===");
        int passed = 0;
        int failed = 0;

        List<RecoveryReportItem> items = new ArrayList<>();
        items.add(new RecoveryReportItem(
                "carved_0x00000200.jpg",
                "JPEG",
                512,
                "Offset 512 (Sector 1)",
                125400,
                "EXACT",
                "85% (Excellent)",
                "RECOVERED"
        ));
        items.add(new RecoveryReportItem(
                "carved_0x00020000.png",
                "PNG",
                131072,
                "Offset 131072 (Sector 256)",
                54200,
                "TRUNCATED",
                "65% (Good)",
                "DISCOVERED"
        ));

        RecoveryReport report = new RecoveryReport(
                "test-disk.img",
                "/tmp/test-disk.img",
                "SIGNATURE CARVING",
                2,
                1,
                1,
                0,
                179600,
                items
        );

        // Test 1: Plaintext generation
        String plainText = report.toPlainText();
        if (plainText.contains("MINION URE RECOVERY REPORT") &&
            plainText.contains("carved_0x00000200.jpg") &&
            plainText.contains("SIGNATURE CARVING") &&
            plainText.contains("RECOVERED")) {
            System.out.println("[PASS] Plaintext report generated with valid structure and items");
            passed++;
        } else {
            System.err.println("[FAIL] Plaintext report content missing expected fields");
            failed++;
        }

        // Test 2: CSV generation
        String csv = report.toCsv();
        if (csv.contains("Index,File Name,Format") &&
            csv.contains("carved_0x00000200.jpg,JPEG") &&
            csv.contains("EXACT") &&
            csv.contains("test-disk.img")) {
            System.out.println("[PASS] CSV report generated with valid RFC-4180 format");
            passed++;
        } else {
            System.err.println("[FAIL] CSV report content missing expected fields");
            failed++;
        }

        // Test 3: Export to disk (txt)
        Path tempTxt = Files.createTempFile("ure-test-report", ".txt");
        try {
            report.export(tempTxt);
            if (Files.exists(tempTxt) && Files.size(tempTxt) > 0) {
                System.out.println("[PASS] Text report exported to disk successfully");
                passed++;
            } else {
                System.err.println("[FAIL] Text report file was empty or missing");
                failed++;
            }
        } finally {
            Files.deleteIfExists(tempTxt);
        }

        // Test 4: Export to disk (csv)
        Path tempCsv = Files.createTempFile("ure-test-report", ".csv");
        try {
            report.export(tempCsv);
            if (Files.exists(tempCsv) && Files.size(tempCsv) > 0) {
                System.out.println("[PASS] CSV report exported to disk successfully");
                passed++;
            } else {
                System.err.println("[FAIL] CSV report file was empty or missing");
                failed++;
            }
        } finally {
            Files.deleteIfExists(tempCsv);
        }

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }
}
