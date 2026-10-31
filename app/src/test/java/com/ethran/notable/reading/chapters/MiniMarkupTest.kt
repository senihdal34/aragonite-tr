package com.ethran.notable.reading.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMarkupTest {
    private fun opens(s: String) = MiniMarkup.parse(s).filterIsInstance<Markup.Open>()
    private fun texts(s: String) = MiniMarkup.parse(s).filterIsInstance<Markup.Text>().joinToString("") { it.text }

    @Test fun tagsAttributesAndNamespaces() {
        val o = opens("""<nav epub:type="toc" id='n1' hidden data-x=5><dc:title>T</dc:title><br/></nav>""")
        assertEquals(listOf("nav", "title", "br"), o.map { it.name })
        assertEquals("toc", o[0].attrs["type"])          // the prefix is dropped
        assertEquals("n1", o[0].attrs["id"])
        assertEquals("", o[0].attrs["hidden"])
        assertEquals("5", o[0].attrs["data-x"])
        assertTrue(o[2].selfClosing)
    }

    @Test fun entitiesIncludingHtmlOnesAndNumbers() {
        assertEquals("a & b < c d é é é —", texts("a &amp; b &lt; c&nbsp;d &eacute; &#233; &#xE9;&#x20;&mdash;"))
        assertEquals("&unknown; and &", texts("&unknown; and &amp;"))
    }

    @Test fun aDoctypeWithAnInternalSubsetCommentsAndInstructionsAreSkipped() {
        val s = """<?xml version="1.0"?><!DOCTYPE html [ <!ENTITY x "y"> ]><!-- <a href="no"> --><html><a href="yes">Go</a></html>"""
        assertEquals(listOf("html", "a"), opens(s).map { it.name })
        assertEquals("yes", opens(s)[1].attrs["href"])
        assertEquals("Go", texts(s))
    }

    @Test fun scriptAndStyleContentIsNotMarkup() {
        val s = "<p>x</p><script>if (a < b) { document.write('<a href=\"no\">') }</script><p>y</p>"
        assertEquals(listOf("p", "script", "p"), opens(s).map { it.name })
        assertEquals("xy", texts(s))
    }

    @Test fun cdataIsText() {
        assertEquals("1 < 2", texts("<t><![CDATA[1 < 2]]></t>"))
    }

    @Test fun brokenMarkupDoesNotStopTheReader() {
        val tokens = MiniMarkup.parse("<a href=\"x>unterminated")
        assertTrue(tokens.isNotEmpty())
        assertEquals(listOf("p"), opens("5 < 6 <p>ok").map { it.name })
    }

    @Test fun bytesAreDecodedByBomOrDeclaredEncoding() {
        assertEquals("é", MiniMarkup.decodeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "é".toByteArray()))
        val latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><t>é</t>".toByteArray(Charsets.ISO_8859_1)
        assertTrue(MiniMarkup.decodeBytes(latin).endsWith("<t>é</t>"))
        assertEquals("<t>é</t>", MiniMarkup.decodeBytes("<t>é</t>".toByteArray(Charsets.UTF_8)))
    }
}
