package com.ganim.nonogram.puzzle.pack

import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle

/**
 * Binary codec for the bundled puzzle pack (build plan 4.5).
 *
 * The plan's constraints, and how the format meets them:
 *
 *  - *"Pack the solution as a bitset, not JSON booleans."* Each record stores the grid
 *    as packed bits - 50 bytes for a 20x20 - plus a four-byte header.
 *  - *"Derive clues at load time rather than storing them."* Clues are recomputed by
 *    [Puzzle.fromSolution] on decode. Storing them would roughly triple the pack.
 *  - *"A small header (version, count, index offsets) so individual puzzles can be read
 *    without parsing the whole file."* [decodeAt] seeks straight to one record, so the
 *    archive screen can page through 5,000 puzzles without materialising them all.
 *  - *"If the pack exceeds 1 MB, the encoding is wrong."* [estimatedSize] makes the
 *    budget checkable before writing.
 *
 * ## Layout
 * ```
 * 0    magic       4 bytes, "NNPK"
 * 4    version     u16
 * 6    flags       u16   (reserved, zero)
 * 8    count       u32
 * 12   index       count * u32, absolute byte offset of each record
 * ...  records
 *
 * record:
 *   width       u8
 *   height      u8
 *   difficulty  u8   (explicit code, see difficultyCode - deliberately not the enum ordinal)
 *   solveDepth  u8
 *   nameLength  u8   (v2; zero for the generated puzzles, which have no name)
 *   name        nameLength bytes, UTF-8
 *   bitset      ceil(width * height / 8) bytes, cell i at byte i/8 bit i%8, LSB first
 * ```
 *
 * Version 2 added the name, for the hand-drawn picture puzzles. Generated puzzles carry
 * an empty name and cost one extra byte each - about 5 KB across the whole pack, which
 * is a fair price for not needing a second format.
 * Integers are big-endian. Records appear in the order given to [encode], and the daily
 * puzzle selector depends on that order being stable across regenerations.
 */
object PuzzlePack {

    const val MAGIC = "NNPK"
    const val VERSION = 2

    private const val HEADER_BYTES = 12
    private const val INDEX_ENTRY_BYTES = 4
    private const val RECORD_HEADER_BYTES = 4

    /** Pack metadata, readable without decoding any puzzle. */
    data class Header(val version: Int, val count: Int)

    fun encode(puzzles: List<Puzzle>): ByteArray {
        require(puzzles.isNotEmpty()) { "Refusing to write an empty pack" }
        puzzles.forEach { puzzle ->
            require(puzzle.width in 1..255 && puzzle.height in 1..255) {
                "Puzzle ${puzzle.id} is ${puzzle.width}x${puzzle.height}; the format stores each side in one byte"
            }
            require(puzzle.solveDepth in 0..255) {
                "Puzzle ${puzzle.id} has solveDepth ${puzzle.solveDepth}; the format stores it in one byte"
            }
        }

        val nameBytes = puzzles.map { it.name.toByteArray(Charsets.UTF_8) }
        nameBytes.forEachIndexed { i, bytes ->
            require(bytes.size <= 255) { "Puzzle ${puzzles[i].id} has a name longer than 255 bytes" }
        }
        val recordSizes = puzzles.mapIndexed { i, puzzle ->
            RECORD_HEADER_BYTES + 1 + nameBytes[i].size + (puzzle.cellCount + 7) / 8
        }
        val indexBytes = puzzles.size * INDEX_ENTRY_BYTES
        val total = HEADER_BYTES + indexBytes + recordSizes.sum()
        val out = ByteArray(total)

        var at = 0
        MAGIC.forEach { out[at++] = it.code.toByte() }
        at = writeU16(out, at, VERSION)
        at = writeU16(out, at, 0)
        at = writeU32(out, at, puzzles.size)

        var recordAt = HEADER_BYTES + indexBytes
        for (size in recordSizes) {
            at = writeU32(out, at, recordAt)
            recordAt += size
        }

        puzzles.forEachIndexed { i, puzzle ->
            out[at++] = puzzle.width.toByte()
            out[at++] = puzzle.height.toByte()
            out[at++] = difficultyCode(puzzle.difficulty).toByte()
            out[at++] = puzzle.solveDepth.toByte()
            out[at++] = nameBytes[i].size.toByte()
            nameBytes[i].copyInto(out, at)
            at += nameBytes[i].size
            val bits = puzzle.bitset()
            bits.copyInto(out, at)
            at += bits.size
        }

        check(at == total) { "Wrote $at bytes into a $total byte pack" }
        return out
    }

    fun readHeader(bytes: ByteArray): Header {
        require(bytes.size >= HEADER_BYTES) { "Pack is only ${bytes.size} bytes, too short for a header" }
        val magic = String(CharArray(4) { bytes[it].toInt().toChar() })
        require(magic == MAGIC) { "Not a puzzle pack: expected magic $MAGIC, found $magic" }
        val version = readU16(bytes, 4)
        require(version == VERSION) { "Pack version $version, this build reads version $VERSION" }
        return Header(version, readU32(bytes, 8))
    }

    /** Decodes a single puzzle without touching the rest of the pack. */
    fun decodeAt(bytes: ByteArray, index: Int): Puzzle {
        val header = readHeader(bytes)
        require(index in 0 until header.count) {
            "Puzzle index $index out of range, pack holds ${header.count}"
        }
        val offset = readU32(bytes, HEADER_BYTES + index * INDEX_ENTRY_BYTES)
        return decodeRecord(bytes, offset)
    }

    fun decodeAll(bytes: ByteArray): List<Puzzle> {
        val header = readHeader(bytes)
        return List(header.count) { i ->
            decodeRecord(bytes, readU32(bytes, HEADER_BYTES + i * INDEX_ENTRY_BYTES))
        }
    }

    private fun decodeRecord(bytes: ByteArray, offset: Int): Puzzle {
        require(offset + RECORD_HEADER_BYTES <= bytes.size) { "Record offset $offset runs past the pack" }
        val width = bytes[offset].toInt() and 0xFF
        val height = bytes[offset + 1].toInt() and 0xFF
        val difficulty = difficultyFor(bytes[offset + 2].toInt() and 0xFF)
        val solveDepth = bytes[offset + 3].toInt() and 0xFF
        val nameLength = bytes[offset + 4].toInt() and 0xFF
        val name = if (nameLength == 0) {
            ""
        } else {
            String(bytes, offset + RECORD_HEADER_BYTES + 1, nameLength, Charsets.UTF_8)
        }
        val cells = Grid.fromBitset(bytes, width * height, offset + RECORD_HEADER_BYTES + 1 + nameLength)
        // Clues and id are derived here rather than stored, per plan 4.4.
        return Puzzle.fromSolution(width, height, cells, difficulty, solveDepth, name)
    }

    /** A puzzle's shape and rating, without its grid or clues. */
    data class Summary(
        val index: Int,
        val id: String,
        /** The picture's name, or empty for a generated puzzle. */
        val name: String,
        val width: Int,
        val height: Int,
        val difficulty: Difficulty,
        val solveDepth: Int,
    )

    /**
     * Indexes the whole pack without decoding a single grid.
     *
     * The archive needs size, difficulty and id for all 5,000 puzzles to filter and to
     * look up completion state. Going through [decodeAll] for that would unpack 5,000
     * bitsets into BooleanArrays and derive 50,000 clue lists, all to read four fields.
     * This reads the record headers and hashes the packed bytes in place.
     */
    fun summaries(bytes: ByteArray): List<Summary> {
        val header = readHeader(bytes)
        return List(header.count) { i ->
            val offset = readU32(bytes, HEADER_BYTES + i * INDEX_ENTRY_BYTES)
            val width = bytes[offset].toInt() and 0xFF
            val height = bytes[offset + 1].toInt() and 0xFF
            val nameLength = bytes[offset + 4].toInt() and 0xFF
            val bitsetAt = offset + RECORD_HEADER_BYTES + 1 + nameLength
            val bitsetLength = (width * height + 7) / 8
            Summary(
                index = i,
                id = Puzzle.stableIdFromBitset(width, height, bytes, bitsetAt, bitsetLength),
                name = if (nameLength == 0) {
                    ""
                } else {
                    String(bytes, offset + RECORD_HEADER_BYTES + 1, nameLength, Charsets.UTF_8)
                },
                width = width,
                height = height,
                difficulty = difficultyFor(bytes[offset + 2].toInt() and 0xFF),
                solveDepth = bytes[offset + 3].toInt() and 0xFF,
            )
        }
    }

    /** Byte size [encode] would produce, for checking against the plan's 1 MB ceiling. */
    fun estimatedSize(puzzles: List<Puzzle>): Int =
        HEADER_BYTES + puzzles.size * INDEX_ENTRY_BYTES +
            puzzles.sumOf {
                RECORD_HEADER_BYTES + 1 + it.name.toByteArray(Charsets.UTF_8).size +
                    (it.cellCount + 7) / 8
            }

    /**
     * Explicit on-disk codes. Not [Enum.ordinal]: reordering the enum would silently
     * reinterpret every pack ever written, and packs are committed to the repo.
     */
    private fun difficultyCode(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 0
        Difficulty.MEDIUM -> 1
        Difficulty.HARD -> 2
        Difficulty.EXPERT -> 3
    }

    private fun difficultyFor(code: Int): Difficulty = when (code) {
        0 -> Difficulty.EASY
        1 -> Difficulty.MEDIUM
        2 -> Difficulty.HARD
        3 -> Difficulty.EXPERT
        else -> throw IllegalArgumentException("Unknown difficulty code $code in pack")
    }

    private fun writeU16(out: ByteArray, at: Int, value: Int): Int {
        out[at] = (value ushr 8).toByte()
        out[at + 1] = value.toByte()
        return at + 2
    }

    private fun writeU32(out: ByteArray, at: Int, value: Int): Int {
        out[at] = (value ushr 24).toByte()
        out[at + 1] = (value ushr 16).toByte()
        out[at + 2] = (value ushr 8).toByte()
        out[at + 3] = value.toByte()
        return at + 4
    }

    private fun readU16(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun readU32(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
}
