# Universal Recovery Engine (URE)

> A modular, cross-platform file recovery engine built in Java for raw disk analysis and data recovery from FAT32 and NTFS filesystems.

**URE (Universal Recovery Engine)** is a Java-based file recovery system designed to explore and implement the low-level mechanisms behind modern data recovery software.

Instead of relying only on operating-system file APIs, URE works closer to the storage layer — reading raw sectors, identifying partition structures, scanning disk regions, interpreting filesystem metadata, analyzing recoverable data, and safely writing recovered content to a separate destination.

---

## How URE Works

URE follows a layered recovery pipeline:

```text
Disk / Disk Image
       │
       ▼
  SectorReader
       │
       ▼
┌───────────────┐
│ MBRParser     │
│ GPTParser     │
└───────┬───────┘
        │
        ▼
 FAT32 / NTFS
Filesystem Analysis
        │
        ▼
   DiskScanner
        │
        ▼
 RecoveryEngine
        │
        ▼
 RecoveryResult
        │
        ▼
 RecoveryWriter
        │
        ▼
  Recovered Data
```

The graphical interface provides access to this recovery pipeline while keeping low-level recovery logic separated from the presentation layer.

---

## Core Modules

### `SectorReader`

Provides low-level access to raw disk sectors using Java random-access I/O.

It converts logical sector addresses into byte offsets and provides sector data to higher-level components.

### `MBRParser`

Analyzes the Master Boot Record located at LBA 0 and validates MBR structures used by legacy partitioned disks.

### `GPTParser`

Handles GUID Partition Table structures used by modern storage devices and provides GPT-aware partition analysis.

### `DiskScanner`

Scans configurable ranges of sectors and analyzes their contents to identify useful data for higher-level recovery operations.

### `ThreadManager`

Provides controlled concurrent execution for suitable scanning and recovery tasks.

### `RecoveryEngine`

Coordinates recovery analysis by connecting scanning operations with higher-level recovery logic.

### `RecoveryResult`

Shared data model used to transfer recovery statistics and results between different components of URE.

### `RecoveryWriter`

Handles the output stage of the recovery pipeline and writes recovered data to a separate destination.

---

## Filesystem & Recovery Layer

URE currently focuses on:

* FAT32 filesystem analysis
* NTFS filesystem analysis
* MBR partition structures
* GPT partition structures
* Raw sector scanning
* Filesystem-aware recovery
* Signature-based recovery
* Safe recovered-data output

The modular design allows additional filesystems and recovery techniques to be introduced without redesigning the entire engine.

---

## Project Architecture

```text
URE/
├── api/                 # Public/API layer
│
├── common/
│   ├── constants/       # Shared constants
│   ├── model/           # Shared data models
│   └── util/            # Utility components
│
├── core/
│   ├── disk/            # Raw sector access
│   ├── partition/       # MBR & GPT parsing
│   ├── recovery/        # Core recovery engine/writer
│   ├── scan/            # Disk scanning
│   └── threading/       # Concurrent task management
│
├── filesystem/
│   ├── fat32/           # FAT32 structures and parsing
│   └── ntfs/            # NTFS structures and parsing
│
├── scanner/             # Higher-level scanning
│
├── recovery/
│   ├── fat32/           # FAT32 recovery
│   ├── ntfs/            # NTFS recovery
│   └── signature/       # Signature-based recovery
│
├── gui/
│   ├── dashboard/
│   ├── scan/
│   ├── preview/
│   ├── recovery/
│   ├── results/
│   └── settings/
│
├── tests/               # Core/module tests
├── testing/             # Integration, GUI & performance tests
├── test-images/         # Local recovery test images
├── docs/                # Technical documentation
├── reports/             # Project/module reports
├── resources/           # Application resources
├── samples/             # Sample data
└── scripts/             # Development scripts
```

---

## Technology Stack

| Area             | Technology                   |
| ---------------- | ---------------------------- |
| Core Language    | Java                         |
| User Interface   | JavaFX                       |
| Filesystems      | FAT32, NTFS                  |
| Partition Tables | MBR, GPT                     |
| Disk Access      | Java Random Access I/O       |
| Concurrency      | Java Threading / Executors   |
| Version Control  | Git                          |
| Repository       | GitHub                       |
| Testing          | Module & Integration Testing |

---

## Development Workflow

URE follows a feature-branch development model.

```text
feature/fahim-core ──────┐
                         │
feature/hennad-filesystem-* ────┼──► develop ──► Integration Testing
                         │                     │
feature/adil-gui ────────┘                     ▼
                                               main
```

Modules are developed and tested independently before being merged into `develop`.

After integration, the combined project is compiled and tested before stable changes are promoted to `main`.

---

## Project Goals

URE is being developed to demonstrate and explore:

* Low-level disk I/O
* Binary data processing
* Partition-table parsing
* FAT32 and NTFS filesystem structures
* Raw disk scanning
* File recovery techniques
* Signature-based file carving
* Concurrent processing
* Modular software architecture
* Java application development
* Automated/module testing
* Git-based collaborative development

---

## Current Status

🚧 **Active Development**

Core disk access, partition parsing, scanning, recovery coordination, threading, recovery result handling, and recovery output components have been implemented and tested.

Filesystem, scanner, recovery, GUI, and full-system integration are under active development.

---

## Safety Notice

> **URE is currently an educational and experimental project.**
>
> Recovery operations should be performed on disk images or test devices whenever possible. Do not use the current development version on important storage devices without backups and proper testing.

---

## Future Development

Planned improvements include:

* Complete FAT32 recovery pipeline
* Complete NTFS recovery pipeline
* File signature carving
* Recovery preview
* Improved multithreaded scanning
* JavaFX recovery dashboard
* Progress monitoring
* Extended filesystem support
* Performance benchmarking
* Full integration and recovery testing

---

## Contributors

Developed as a collaborative software engineering project focused on understanding the internal architecture of file recovery systems.

---

**Universal Recovery Engine — Recovering data from the storage layer up.**
