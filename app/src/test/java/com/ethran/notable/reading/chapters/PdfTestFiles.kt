package com.ethran.notable.reading.chapters

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/** Writes small PDFs by hand: every byte of them is in the test, so a parser bug cannot hide in a generator. */
internal object PdfTestFiles {

    /** The bytes of "n 0 obj ... endobj" for [body], which may be a dictionary, an array or a number. */
    private fun obj(num: Int, body: String) = "$num 0 obj\n$body\nendobj\n"

    /**
     * The outline fixture: 5 pages (objects 3..7), outline items 10.., a name tree (8) and the old /Dests dictionary.
     * Items: Intro -> page 1 (Dest array), Part A -> page 2 with child "Chapter A1" (GoTo action) -> page 3,
     * "Named" -> name tree entry -> page 4, "Old name" -> /Dests entry -> page 5, "By number" (Dest [2 /Fit], page index 2) -> page 3,
     * "Lost" -> a page that does not exist (dropped from the chapters, kept as page -1 in the bookmarks).
     */
    fun bodies(): LinkedHashMap<Int, String> {
        val m = LinkedHashMap<Int, String>()
        m[1] = "<< /Type /Catalog /Pages 2 0 R /Outlines 9 0 R /Names << /Dests 8 0 R >> /Dests << /old.sec [7 0 R /Fit] >> >>"
        m[2] = "<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R 6 0 R 7 0 R] /Count 5 >>"
        for (p in 3..7) m[p] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>"
        m[8] = "<< /Names [(sec.4) [6 0 R /Fit] (zzz) [7 0 R /Fit]] >>"
        m[9] = "<< /Type /Outlines /First 10 0 R /Last 15 0 R /Count 6 >>"
        m[10] = "<< /Title (Intro) /Parent 9 0 R /Next 11 0 R /Dest [3 0 R /XYZ 0 0 0] >>"
        m[11] = "<< /Title <FEFF00C700FC0072> /Parent 9 0 R /Prev 10 0 R /Next 13 0 R /First 12 0 R /Last 12 0 R /Count 1 /Dest [4 0 R /Fit] >>"
        m[12] = "<< /Title (Chapter A1) /Parent 11 0 R /A << /S /GoTo /D [5 0 R /Fit] >> >>"
        m[13] = "<< /Title (Named) /Parent 9 0 R /Prev 11 0 R /Next 14 0 R /Dest (sec.4) >>"
        m[14] = "<< /Title (Old name) /Parent 9 0 R /Prev 13 0 R /Next 15 0 R /Dest /old.sec >>"
        m[15] = "<< /Title (By number) /Parent 9 0 R /Prev 14 0 R /Next 16 0 R /Dest [2 /Fit] >>"
        m[16] = "<< /Title (Lost) /Parent 9 0 R /Prev 15 0 R /Dest [99 0 R /Fit] >>"
        return m
    }

    /** A classic file: header, objects, one xref table, trailer. */
    fun classic(bodies: Map<Int, String>, root: Int = 1, prefix: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(prefix.toByteArray(Charsets.ISO_8859_1))
        val start = out.size()
        out.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = HashMap<Int, Int>()
        for ((n, b) in bodies) { offsets[n] = out.size() - start; out.write(obj(n, b).toByteArray(Charsets.ISO_8859_1)) }
        val size = bodies.keys.max() + 1
        val xrefAt = out.size() - start
        out.write("xref\n0 $size\n".toByteArray())
        for (n in 0 until size) {
            val line = if (n == 0) "0000000000 65535 f \n" else offsets[n]?.let { "%010d 00000 n \n".format(it) } ?: "0000000000 00000 f \n"
            out.write(line.toByteArray())
        }
        out.write("trailer\n<< /Size $size /Root $root 0 R >>\nstartxref\n$xrefAt\n%%EOF\n".toByteArray())
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(data); d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    /** A PDF 1.5 file: the catalog, page tree, outlines in a Flate object stream, a Flate xref stream with PNG "Up" prediction. */
    fun withObjectStream(): ByteArray {
        val all = bodies()
        val inStream = all.keys.filter { it == 1 || it == 2 || it >= 8 }       // the pages themselves stay plain objects
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.5\n".toByteArray())
        val offsets = HashMap<Int, Int>()
        for (p in 3..7) { offsets[p] = out.size(); out.write(obj(p, all.getValue(p)).toByteArray()) }
        // The object stream: "num offset" pairs, then the objects.
        val body = StringBuilder()
        val header = StringBuilder()
        for (n in inStream) { header.append("$n ${body.length} "); body.append(all.getValue(n)).append('\n') }
        val first = header.length
        val data = deflate((header.toString() + body.toString()).toByteArray())
        val objStmNum = 20
        offsets[objStmNum] = out.size()
        out.write("$objStmNum 0 obj\n<< /Type /ObjStm /N ${inStream.size} /First $first /Filter /FlateDecode /Length ${data.size} >>\nstream\n".toByteArray())
        out.write(data); out.write("\nendstream\nendobj\n".toByteArray())
        // The xref stream: rows of [type:1][field2:4][field3:2] for objects 0..21, "Up" predicted (PNG filter 2) then deflated.
        val xrefNum = 21
        val xrefAt = out.size()
        offsets[xrefNum] = xrefAt
        val rows = ArrayList<ByteArray>()
        fun row(type: Int, f2: Int, f3: Int) = byteArrayOf(type.toByte(), (f2 shr 24).toByte(), (f2 shr 16).toByte(), (f2 shr 8).toByte(), f2.toByte(), (f3 shr 8).toByte(), f3.toByte())
        for (n in 0..xrefNum) {
            rows.add(
                when {
                    n == 0 -> row(0, 0, 65535)
                    n in inStream -> row(2, objStmNum, inStream.indexOf(n))
                    offsets.containsKey(n) -> row(1, offsets.getValue(n), 0)
                    else -> row(0, 0, 0)
                }
            )
        }
        val predicted = ByteArrayOutputStream()
        var prev = ByteArray(7)
        for (r in rows) {
            predicted.write(2)
            for (i in r.indices) predicted.write((r[i] - prev[i]) and 0xFF)
            prev = r
        }
        val xdata = deflate(predicted.toByteArray())
        out.write(
            ("$xrefNum 0 obj\n<< /Type /XRef /Size ${xrefNum + 1} /W [1 4 2] /Root 1 0 R /Filter /FlateDecode " +
                "/DecodeParms << /Predictor 12 /Columns 7 >> /Length ${xdata.size} >>\nstream\n").toByteArray()
        )
        out.write(xdata); out.write("\nendstream\nendobj\n".toByteArray())
        out.write("startxref\n$xrefAt\n%%EOF\n".toByteArray())
        return out.toByteArray()
    }

    /** [original] with an incremental update appended: object 10's title becomes [newTitle], with its own xref section and /Prev. */
    fun withUpdate(original: ByteArray, newTitle: String, prevXref: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(original)
        val at = out.size()
        out.write(obj(10, "<< /Title ($newTitle) /Parent 9 0 R /Next 11 0 R /Dest [3 0 R /XYZ 0 0 0] >>").toByteArray())
        val xrefAt = out.size()
        out.write("xref\n0 1\n0000000000 65535 f \n10 1\n%010d 00000 n \n".format(at).toByteArray())
        out.write("trailer\n<< /Size 17 /Root 1 0 R /Prev $prevXref >>\nstartxref\n$xrefAt\n%%EOF\n".toByteArray())
        return out.toByteArray()
    }
}
