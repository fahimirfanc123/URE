# MINION URE — Universal Recovery Engine

**MINION URE** is a cross-platform file recovery engine built in **Java** with a **JavaFX desktop interface**.

The project is designed to analyze storage devices and disk images at the sector and filesystem level, detect recoverable data, and recover deleted files from **FAT32** and **NTFS** filesystems.

> **Current Status:** Active Development — FAT32 and NTFS deleted-file recovery are operational.

---

## Features

### Core Recovery Engine

- Raw sector-level disk access
- Sector scanning and data detection
- MBR partition validation
- GPT partition parsing
- Multithreaded task support
- Recovery analysis
- Raw sector recovery
- Recovery output writer

### FAT32 Recovery

- FAT32 boot sector parsing
- Filesystem geometry detection
- Root directory analysis
- Deleted directory-entry detection
- Deleted 8.3 filename extraction
- Starting-cluster detection
- Deleted file size extraction
- Deleted file recovery
- FAT32 recovery GUI
- Recovery risk / fragmentation analysis foundation

### NTFS Recovery

- NTFS boot sector parsing
- NTFS filesystem geometry detection
- Master File Table (`$MFT`) access
- MFT record reading
- MFT record parsing
- Deleted file detection
- `$FILE_NAME` metadata extraction
- Resident `$DATA` recovery
- Non-resident `$DATA` recovery
- NTFS data-run / runlist parsing
- Sparse-run handling
- Deleted-file recovery GUI

### Desktop Interface

- Custom MINION URE application shell
- Drive / disk-image selection
- Live scanning
- Scan progress
- Scan results
- Recovery analysis
- Recovery mode selection
- Raw recovery
- FAT32 deleted-file recovery
- NTFS deleted-file recovery
- Recovery output selection
- Custom dark Ubuntu-inspired interface

---

## Recovery Pipeline

```text
              Disk / Disk Image
                     │
                     ▼
               SectorReader
                     │
                     ▼
            Partition Analysis
              MBR / GPT Parser
                     │
                     ▼
             Filesystem Layer
               FAT32 / NTFS
                     │
                     ▼
                DiskScanner
                     │
                     ▼
              RecoveryEngine
                     │
                     ▼
             Recovery Analysis
                     │
                     ▼
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
   Raw Recovery   FAT32        NTFS
                  Recovery     Recovery
        │            │            │
        └────────────┼────────────┘
                     │
                     ▼
              RecoveryWriter
                     │
                     ▼
              Recovered File
```

---

## FAT32 Recovery Pipeline

```text
FAT32 Disk / Image
        │
        ▼
FAT32BootSector
        │
        ▼
Filesystem Geometry
        │
        ▼
FAT32DeletedFileScanner
        │
        ▼
Deleted Directory Entry
        │
        ├── Filename
        ├── File Size
        └── Starting Cluster
        │
        ▼
FAT32FileRecovery
        │
        ▼
RecoveryWriter
        │
        ▼
Recovered File
```

---

## NTFS Recovery Pipeline

```text
NTFS Disk / Image
        │
        ▼
NTFSBootSector
        │
        ▼
NTFSMFTReader
        │
        ▼
$MFT Records
        │
        ▼
NTFSMFTParser
        │
        ▼
NTFSDeletedFileScanner
        │
        ▼
Deleted File Entry
        │
        ▼
NTFSFileRecovery
        │
        ├── Resident $DATA
        │
        └── Non-Resident $DATA
                 │
                 ▼
          NTFSRunListParser
                 │
                 ▼
             Data Runs
        │
        ▼
RecoveryWriter
        │
        ▼
Recovered File
```

---

## NTFS Non-Resident Recovery Validation

MINION URE has successfully recovered a deleted **2 MiB NTFS non-resident file** from a test disk image.

The original and recovered files were verified using SHA-256.

```text
Original:
cbec94bf28ad0a1b60863f2350bc4b113dc984040ff9f4332867a4f4fe10cde4

Recovered:
cbec94bf28ad0a1b60863f2350bc4b113dc984040ff9f4332867a4f4fe10cde4
```

The identical hashes confirm that the recovered test file matched the original **byte-for-byte**.

---

## Project Structure

```text
URE/
│
├── api/
│
├── common/
│   ├── constants/
│   ├── model/
│   └── util/
│
├── core/
│   ├── disk/
│   ├── partition/
│   ├── recovery/
│   ├── scan/
│   └── threading/
│
├── filesystem/
│   ├── fat32/
│   └── ntfs/
│
├── gui/
│
├── recovery/
│
├── resources/
│   └── css/
│
├── tests/
│   ├── core/
│   └── filesystem/
│       ├── fat32/
│       └── ntfs/
│
├── docs/
├── scripts/
├── samples/
└── README.md
```

---

## Important Components

### `SectorReader`

Provides low-level access to sectors inside a disk or disk-image file.

It forms the foundation of the recovery engine.

### `DiskScanner`

Scans sectors and determines whether they contain data or are empty.

### `RecoveryEngine`

Analyzes scanned sectors and provides recovery statistics used by the recovery workflow.

### `RecoveryWriter`

Writes recovered byte data safely to an output file selected by the user.

### `FAT32BootSector`

Parses FAT32 filesystem parameters including:

- Bytes per sector
- Sectors per cluster
- Reserved sectors
- FAT count
- FAT size
- Root cluster
- First data sector

### `FAT32DeletedFileScanner`

Searches FAT32 directory entries for deleted files.

### `FAT32FileRecovery`

Reconstructs deleted FAT32 file data from its starting cluster and metadata.

### `NTFSBootSector`

Parses important NTFS filesystem parameters including:

- Bytes per sector
- Sectors per cluster
- Total sectors
- MFT location
- MFT mirror location
- File-record configuration

### `NTFSMFTReader`

Locates and reads records from the NTFS Master File Table.

### `NTFSMFTParser`

Parses MFT records and extracts filesystem metadata used for deleted-file detection.

### `NTFSDeletedFileScanner`

Searches MFT records for deleted file entries.

### `NTFSRunListParser`

Parses NTFS mapping pairs used by non-resident `$DATA` attributes and converts them into physical cluster runs.

### `NTFSFileRecovery`

Recovers NTFS files from `$DATA` attributes.

Current support includes:

- Resident data
- Non-resident data
- Multiple data runs
- Sparse runs

---

## GUI Workflow

```text
MINION URE
    │
    ▼
Home
    │
    ▼
Drive / Image Selection
    │
    ▼
Live Scan
    │
    ▼
Scan Results
    │
    ▼
Recovery Analysis
    │
    ▼
Select Recovery Method
    │
    ├── Raw Sector Recovery
    │
    ├── FAT32 File Recovery
    │
    └── NTFS File Recovery
            │
            ▼
      Select Deleted File
            │
            ▼
          Recover
            │
            ▼
       Save Output
```

---

## Testing

The project contains standalone tests for major recovery components.

Current tested areas include:

- Sector reading
- MBR parsing
- GPT parsing
- Disk scanning
- Recovery engine
- Recovery writer
- FAT32 boot-sector parsing
- FAT32 deleted-file scanning
- FAT32 deleted-file recovery
- NTFS boot-sector parsing
- NTFS MFT reading
- NTFS deleted-file scanning
- NTFS resident recovery
- NTFS non-resident recovery

Real FAT32 and NTFS filesystem images are used during recovery testing.

---

## Current Limitations

MINION URE is still under development.

Current limitations include:

- FAT32 deleted-file recovery currently focuses on basic directory entries.
- FAT32 long filename reconstruction is not yet complete.
- FAT32 fragmented-file recovery requires further development.
- Recursive FAT32 subdirectory recovery is not yet complete.
- NTFS MFT scanning currently uses a bounded record range.
- Fully fragmented `$MFT` handling is not yet implemented.
- NTFS compressed and encrypted file recovery is not supported.
- NTFS attribute-list handling requires further development.
- Recovery success depends on whether deleted data has already been overwritten.
- Formatted-drive signature carving is not yet implemented.

---

## Planned Development

Upcoming work includes:

- Recovery safety and overwrite analysis
- Recovery confidence prediction
- Improved FAT32 fragmentation support
- Improved NTFS filesystem handling
- File preview
- File-signature carving
- Formatted-drive recovery
- Recursive directory recovery
- Recovery reports
- Performance optimization
- Additional filesystem testing

---

## Technology

- **Language:** Java
- **GUI:** JavaFX
- **Filesystems:** FAT32, NTFS
- **Development Environment:** Linux / Ubuntu
- **Version Control:** Git & GitHub

---

## Project Status

**MINION URE v0.1 Beta**

```text
Core Sector Engine        ██████████  Working
Raw Recovery              ██████████  Working
FAT32 Recovery            ██████████  Working
NTFS Recovery             ██████████  Working
Recovery GUI              ██████████  Working
Recovery Prediction       ███████░░░  In Development
Signature Carving         ░░░░░░░░░░  Planned
Formatted Drive Recovery  ░░░░░░░░░░  Planned
```

> MINION URE is an educational and experimental recovery engine. Always recover data to a different storage device and avoid writing to a source device containing data you want to recover.

---

## License

License information will be added as the project approaches a stable release.
