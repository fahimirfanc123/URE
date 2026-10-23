package core.disk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Read-only Linux physical-disk inventory.
 *
 * This class only discovers devices. It does not open, mount,
 * unmount, modify, or scan any device.
 */
public final class PhysicalDriveDiscovery {

    private PhysicalDriveDiscovery() {
    }

    public static final class PhysicalDrive {
        private final String name;
        private final String path;
        private final long sizeBytes;
        private final String model;
        private final String transport;
        private final boolean removable;
        private final boolean readOnly;
        private final boolean containsRootFilesystem;
        private final boolean hasMountedFilesystem;

        public PhysicalDrive(
                String name,
                String path,
                long sizeBytes,
                String model,
                String transport,
                boolean removable,
                boolean readOnly,
                boolean containsRootFilesystem,
                boolean hasMountedFilesystem
        ) {
            this.name = name;
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.model = model;
            this.transport = transport;
            this.removable = removable;
            this.readOnly = readOnly;
            this.containsRootFilesystem = containsRootFilesystem;
            this.hasMountedFilesystem = hasMountedFilesystem;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getModel() {
            return model;
        }

        public String getTransport() {
            return transport;
        }

        public boolean isRemovable() {
            return removable;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public boolean containsRootFilesystem() {
            return containsRootFilesystem;
        }

        public boolean hasMountedFilesystem() {
            return hasMountedFilesystem;
        }

        public boolean isSelectable() {
            return sizeBytes > 0
                    && !containsRootFilesystem
                    && !readOnly;
        }
    }

    /**
     * Lists physical disk devices using lsblk JSON output.
     * Requires util-linux lsblk with JSON support.
     */
    public static List<PhysicalDrive> discover() throws IOException {
        Process process = new ProcessBuilder(
                "lsblk",
                "--json",
                "--bytes",
                "--paths",
                "--output",
                "NAME,PATH,TYPE,SIZE,MODEL,TRAN,RM,RO,MOUNTPOINTS"
        ).redirectErrorStream(true).start();

        String output;

        try {
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Timed out while discovering physical drives.");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(),
                            StandardCharsets.UTF_8
                    ))) {
                StringBuilder text = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }

                output = text.toString();
            }

            if (process.exitValue() != 0) {
                throw new IOException("lsblk failed: " + output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Drive discovery was interrupted.", e);
        }

        /*
         * Use a second, simple lsblk invocation to obtain one physical
         * disk per line. JSON is retained above for diagnostics and
         * future partition-tree support.
         */
        return parseFlatInventory(output);
    }

    private static List<PhysicalDrive> parseFlatInventory(String json)
            throws IOException {
        Process process = new ProcessBuilder(
                "lsblk",
                "--noheadings",
                "--pairs",
                "--bytes",
                "--paths",
                "--output",
                "NAME,PATH,TYPE,SIZE,MODEL,TRAN,RM,RO"
        ).redirectErrorStream(true).start();

        String output;

        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out while reading disk inventory.");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(),
                            StandardCharsets.UTF_8
                    ))) {
                StringBuilder text = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }

                output = text.toString();
            }

            if (process.exitValue() != 0) {
                throw new IOException("Could not read disk inventory: " + output.trim());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Drive inventory was interrupted.", e);
        }

        String rootSource = runRootSource();
        java.util.Set<String> rootAncestors =
                resolveRootAncestors(rootSource);

        List<PhysicalDrive> drives = new ArrayList<>();

        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            var fields = parsePairs(line);

            if (!"disk".equals(fields.get("TYPE"))) {
                continue;
            }

            String path = fields.get("PATH");
            if (path == null || !path.startsWith("/dev/")) {
                continue;
            }

            long size;
            try {
                size = Long.parseLong(fields.getOrDefault("SIZE", "0"));
            } catch (NumberFormatException e) {
                size = 0;
            }

            boolean removable = "1".equals(fields.get("RM"));
            boolean readOnly = "1".equals(fields.get("RO"));

            if (!isBlockDevice(path)) {
                continue;
            }

            String canonicalPath;
            try {
                canonicalPath = Path.of(path).toRealPath().toString();
            } catch (IOException e) {
                // Unknown device identity: do not offer this device.
                continue;
            }

            boolean rootDiskMatch = rootAncestors.contains(path)
                    || rootAncestors.contains(canonicalPath);

            // Also compare normalized device paths. Never rely solely on
            // mounted-filesystem detection to protect the system disk.
            if (!rootDiskMatch) {
                try {
                    for (String ancestor : rootAncestors) {
                        if (Path.of(ancestor).normalize()
                                .equals(Path.of(canonicalPath).normalize())) {
                            rootDiskMatch = true;
                            break;
                        }
                    }
                } catch (RuntimeException e) {
                    throw new IOException(
                            "Cannot safely compare system-disk identity for " + path, e
                    );
                }
            }

            boolean mounted = hasMountedFilesystem(path);

            drives.add(new PhysicalDrive(
                    fields.getOrDefault("NAME", path),
                    path,
                    size,
                    fields.getOrDefault("MODEL", "").trim(),
                    fields.getOrDefault("TRAN", "").trim(),
                    removable,
                    readOnly,
                    rootDiskMatch,
                    mounted
            ));
        }

        return Collections.unmodifiableList(drives);
    }

    /**
     * Re-discovers the physical inventory and confirms that this exact
     * block device is still eligible. Any uncertainty propagates as an
     * IOException so callers can refuse to scan.
     */
    public static boolean isSelectableDevice(String requestedPath)
            throws IOException {
        if (requestedPath == null || !requestedPath.startsWith("/dev/")) {
            return false;
        }

        Path requested;
        try {
            requested = Path.of(requestedPath).toRealPath();
        } catch (IOException e) {
            return false;
        }

        if (!isBlockDevice(requested.toString())) {
            return false;
        }

        for (PhysicalDrive drive : discover()) {
            Path discovered;
            try {
                discovered = Path.of(drive.getPath()).toRealPath();
            } catch (IOException e) {
                continue;
            }

            if (requested.equals(discovered)) {
                return drive.isSelectable();
            }
        }

        return false;
    }

    private static boolean isBlockDevice(String path) {
        try {
            Process process = new ProcessBuilder(
                    "test", "-b", path
            ).start();

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static java.util.Map<String, String> parsePairs(String line) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\w+)=\"((?:\\\\.|[^\"])*)\"")
                .matcher(line);

        while (matcher.find()) {
            result.put(
                    matcher.group(1),
                    matcher.group(2)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
            );
        }

        return result;
    }

    private static String runRootSource() throws IOException {
        Process process = new ProcessBuilder(
                "findmnt", "-n", "-o", "SOURCE", "/"
        ).redirectErrorStream(true).start();

        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out while identifying the root filesystem.");
            }

            String source;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(),
                            StandardCharsets.UTF_8
                    ))) {
                source = reader.readLine();
            }

            if (process.exitValue() != 0 || source == null || source.isBlank()) {
                throw new IOException("Cannot identify the root filesystem; refusing to enable physical scanning.");
            }

            return source.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Root-filesystem check was interrupted.", e);
        }
    }

    /**
     * Resolves the root filesystem and all block-device ancestors.
     * Failure is fatal: physical-drive discovery must not guess which
     * disk contains the operating system.
     */
    private static java.util.Set<String> resolveRootAncestors(
            String rootSource
    ) throws IOException {
        Process process = new ProcessBuilder(
                "lsblk", "--inverse", "--list", "--noheadings", "--paths",
                "--output", "NAME", rootSource
        ).redirectErrorStream(true).start();

        String output;
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out resolving system-disk ancestry.");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8
                    ))) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
                output = result.toString();
            }

            if (process.exitValue() != 0 || output.isBlank()) {
                throw new IOException(
                        "Cannot resolve system-disk ancestry; physical scanning is disabled."
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("System-disk ancestry check interrupted.", e);
        }

        java.util.Set<String> ancestors = new java.util.HashSet<>();
        for (String line : output.split("\\R")) {
            String device = line.trim();
            if (device.startsWith("/dev/")) {
                ancestors.add(device);
                try {
                    ancestors.add(Path.of(device).toRealPath().toString());
                } catch (IOException ignored) {
                    // The original path is retained; candidate identity
                    // checks still fail closed if it cannot be resolved.
                }
            }
        }

        if (ancestors.isEmpty()) {
            throw new IOException(
                    "System disk could not be identified; physical scanning is disabled."
            );
        }

        return ancestors;
    }

    /**
     * Checks the disk and all descendants for mounted filesystems.
     * If the check cannot be completed, fail closed.
     */
    private static boolean hasMountedFilesystem(String diskPath)
            throws IOException {
        Process process = new ProcessBuilder(
                "lsblk", "--tree", "--noheadings", "--paths",
                "--output", "MOUNTPOINTS", diskPath
        ).redirectErrorStream(true).start();

        String output;
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(
                        "Timed out checking mounted filesystems for " + diskPath
                );
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8
                    ))) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
                output = result.toString();
            }

            if (process.exitValue() != 0) {
                throw new IOException(
                        "Cannot verify mounted filesystems for " + diskPath
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Mounted-filesystem check interrupted.", e);
        }

        return output.lines().anyMatch(line -> !line.isBlank());
    }
}
