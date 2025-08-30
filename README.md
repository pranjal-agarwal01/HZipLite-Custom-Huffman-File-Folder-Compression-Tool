# HZipLite — Single-File Huffman Compressor (Java/Swing)

**HZipLite** is a lightweight, **lossless** compression tool written in Java. It uses **Huffman coding** to compress and decompress both **individual files** and **entire folders**. A simple **Swing GUI** lets you choose inputs and run compression without command-line arguments. 

---

## What You Get (At a Glance)
- **Language:** Java (JDK 17+)
- **UI:** Swing (desktop GUI)
- **Source:** `HZipLite.java` (single file)
- **Helpers:** `run.bat` (Windows), `run.sh` (macOS/Linux)
- **Archive format:** `.hzip` (Huffman-coded container)

---

## Features
- **Lossless** — restores the exact original bytes
- **Files & Folders** — compress a single file or a whole directory
- **Portable** — pure Java, no external libraries
- **Cross-platform** — Windows, macOS, Linux
- **Simple UI** — click **Compress** / **Decompress** and pick paths
- **Educational** — clear, single-file implementation of Huffman coding

> **Note:** Text (logs/CSV/JSON/source) typically compresses best; already-compressed media (PNG/JPG/MP4/ZIP) may not shrink much.

---

## Requirements
- **JDK 17+** installed and on your PATH  
  Verify with:
  ```bash
  java -version
  javac -version

## Quick Start (Pick One)

After extracting the project, cd into the project folder (the one containing HZipLite.java).

A) One-Click Scripts (Recommended)
  Windows
 ```bash
   run.bat
 ```

  macOS / Linux
  ```bash
chmod +x run.sh
./run.sh
```
These compile HZipLite.java (if needed) and launch the GUI.

## Using HZipLite (GUI)

Launch the app (via script or java HZipLite).

Click Compress:

Select a file or a folder.

Choose where to save; the app suggests <name>.hzip next to your input.

A summary shows original size, compressed size, and ratio.

Click Decompress:

Select a .hzip file (created by HZipLite).

Choose a restore location.

If the archive contains a file, you get that file back.

If it contains a folder, the full directory tree is restored.

Where are outputs saved? By default next to the input; you can change it in the file chooser.

##Command Examples (If You Prefer Terminal)

```bash
# Compile & run
javac -encoding UTF-8 HZipLite.java && java HZipLite

# Create JAR & run
javac -encoding UTF-8 HZipLite.java
jar cfe HZipLite.jar HZipLite HZipLite.class
java -jar HZipLite.jar
```
## What’s Inside a .hzip (Conceptual)
A .hzip file contains:

A small header (magic, type/file-or-folder flag, minimal metadata)

A Huffman codebook (tree) to decode the payload

The encoded bitstream of your original data

If you chose a folder, HZipLite first creates a ZIP of that folder (to preserve names/paths) and then Huffman-encodes that ZIP. During decompression, the ZIP is decoded and expanded back into a directory.

## Project Structure
```bash
.
├─ HZipLite.java      # main app (GUI + compression logic)
├─ run.bat            # Windows helper
└─ run.sh             # macOS/Linux helper
```

