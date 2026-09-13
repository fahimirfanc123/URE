# Module Documentation: Core - Disk - SectorReader

## 1. Overview
The `SectorReader` class serves as the foundational I/O abstraction layer for the Universal Recovery Engine (URE). Its purpose is to provide structured, low-level read access to physical drives or raw disk image files.

## 2. Architecture & Design
- **Single Responsibility:** Adheres to the Single Responsibility Principle by focusing solely on reading bytes from disk based on a fixed sector size.
- **Robustness:** Implements `Closeable` to support `try-with-resources`, ensuring file handles are properly released even if exceptions occur.
- **Flexibility:** Supports configurable sector sizes (e.g., 512 for legacy, 4096 for modern "4K" disks), preventing architectural bottlenecks for future storage technologies.
- **Safety:** Performs bounds checking on sector indices to prevent reading beyond the file size.

## 3. Key Components
- `readSector(long sectorNumber)`: Reads a single sector.
- `readSectors(long startSector, int count)`: Optimized read for contiguous sectors.
- `getTotalSectors()`: Calculates drive capacity based on underlying file size.

## 4. Usage Example
```java
try (SectorReader reader = new SectorReader("path/to/image.img")) {
    byte[] sectorData = reader.readSector(0); // Read MBR/Boot sector
    // ... process ...
} catch (IOException e) {
    // Handle I/O errors
}
```

## 5. Development Notes
- The class is designed for read-only access (`"r"` mode in `RandomAccessFile`).
- The use of `readFully()` ensures that we do not encounter partial reads, which are catastrophic for filesystem parsing.
