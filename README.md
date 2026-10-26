# MINION URE — Universal Recovery Engine

**Recover what matters. Analyse what remains.**

MINION URE is a student-developed file-recovery project designed to analyse storage sources and help recover files from supported filesystems. The project is under active development; supported features and recovery results should be treated as experimental until thoroughly tested.

> **Beta software:** Always recover files to a different physical drive from the source. Do not use this application on the only copy of important data. Recovery is not guaranteed.

## Screenshots

### Home screen

![MINION URE home screen](docs/screenshots/minion-ure-home.png)

### Select a recovery source

![Recovery source selection screen](docs/screenshots/recovery-source-selection.png)

## Intended features

- **FAT32 recovery** — deleted-file recovery workflow for FAT32 sources.
- **NTFS analysis** — filesystem analysis and recovery workflow, subject to implementation status.
- **Recovery result classification** — `EXACT`, `PARTIAL`, or `FAILED` to distinguish recovery outcomes.
- **Read-only source handling** — designed to analyse the selected source without writing recovered data back to it.
- **Disk image input** — open an existing disk image for analysis.
- **Recovery confidence / AI prediction** — shown in the interface; treat predictions as experimental and do not interpret them as a guarantee of recoverability.

Only claim a feature as fully supported after validating it against representative test images and physical media. The interface may expose features that are still in progress.

## Safety first

1. If data has been deleted from a drive, stop using that drive immediately. New writes can overwrite recoverable data.
2. Prefer creating a disk image and analysing the image when practical.
3. Do not save recovered files onto the source drive or partition.
4. Use a separate destination drive with enough free space.
5. Test with disposable images or USB media containing non-important data before relying on the tool for real recovery.
6. Read-only application logic does not automatically guarantee that every operating-system or hardware path is write-proof. Verify the source-protection behaviour before using important media.

## Typical workflow

1. Connect the USB drive or prepare a disk image.
2. Launch MINION URE.
3. In **Select Recovery Source**, review the detected volumes and physical disks.
4. Select the intended source, checking its name, device path, capacity, and type carefully.
5. If using a disk image, choose **Open Disk Image** and select the image file.
6. Run the available filesystem analysis / scan.
7. Review the files and their recovery status (`EXACT`, `PARTIAL`, or `FAILED`) where available.
8. Select the files to recover.
9. Choose a destination on a different drive and recover the selected files.
10. Open a sample of the recovered files and verify their contents.
11. Close the application and safely eject the USB drive when finished.

**Important:** Do not select your operating-system disk for experiments. In the screenshots, the system disk is disabled in the source list; still verify the selected device before starting any scan.

## Build and run (Ubuntu/Linux)

The commands below reflect the current development setup and may need adjustment for other distributions or JavaFX versions.

### Requirements

- JDK 21
- JavaFX controls libraries
- GCC and `pkg-config` for the native helper
- GLib/GIO development headers and libraries

On Ubuntu-based systems, packages commonly needed for the native build include:

```bash
sudo apt update
sudo apt install build-essential pkg-config libglib2.0-dev
```

Install a compatible JDK and JavaFX package for your distribution. If your JavaFX JARs are located under `/usr/share/java/`, check the exact filenames before using the commands below.

### Compile Java sources

From the repository root:

```bash
cd ~/Projects/URE

JFX="/usr/share/java/javafx-base-11.jar:/usr/share/java/javafx-graphics-11.jar:/usr/share/java/javafx-controls-11.jar"

mkdir -p build/classes

find common core filesystem gui src/main/java \
  -type f -name '*.java' -print0 |
  xargs -0 javac -proc:none \
    --module-path "$JFX" \
    --add-modules javafx.controls \
    -cp "/usr/share/java/*" \
    -d build/classes
```

If any listed source directory does not exist in your checkout, adjust the `find` paths to match your repository. The JavaFX version and JDK must be compatible.

### Build the native read-only bridge

The following command assumes the bridge source exists at the shown path:

```bash
mkdir -p build/native

gcc -O2 -Wall -Wextra -Werror \
  $(pkg-config --cflags gio-2.0 gio-unix-2.0) \
  native/ure-disk-helper/udisks_readonly_bridge.c \
  -o build/native/ure-udisks-readonly-bridge \
  $(pkg-config --libs gio-2.0 gio-unix-2.0)
```

### Launch

```bash
JFX="/usr/share/java/javafx-base-11.jar:/usr/share/java/javafx-graphics-11.jar:/usr/share/java/javafx-controls-11.jar"

java \
  -Dure.native.bridge="$PWD/build/native/ure-udisks-readonly-bridge" \
  --module-path "$JFX" \
  --add-modules javafx.controls \
  -cp "build/classes:resources:/usr/share/java/*" \
  gui.Main
```

These are development commands, not yet a packaged installer. Paths and dependencies may differ on your machine.

## Project status

MINION URE is a work in progress. Before using it for important recovery, validate each supported filesystem and recovery mode using known test data, including deleted files, fragmented files, damaged metadata, empty media, and invalid disk images. Record limitations and known issues as testing progresses.

## Contributing

Issues, bug reports, test cases, and thoughtful contributions are welcome. Please do not submit real personal data, confidential disk images, or other sensitive recovery samples.

When reporting a bug, include:
- operating system and version;
- Java/JDK and JavaFX versions;
- filesystem and source type (disk image or physical device);
- steps to reproduce;
- relevant logs with personal information removed;
- whether the source was disposable test media.

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE) for the full text.
