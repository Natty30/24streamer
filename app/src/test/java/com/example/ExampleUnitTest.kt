package com.example

import com.example.media.VideoMetadataUtil
import com.example.rtmp.amf.Amf0
import com.example.rtmp.client.RtmpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExampleUnitTest {

    @Test
    fun testRtmpUrlParsing() {
        val youtube = RtmpClient.parseUrl("rtmp://a.rtmp.youtube.com/live2", "abcd-1234")
        assertFalse(youtube.isRtmps)
        assertEquals("a.rtmp.youtube.com", youtube.host)
        assertEquals(1935, youtube.port)
        assertEquals("live2", youtube.app)
        assertEquals("abcd-1234", youtube.streamKey)

        val facebook = RtmpClient.parseUrl("rtmps://live-api-s.facebook.com:443/rtmp/", "fb_token_123")
        assertTrue(facebook.isRtmps)
        assertEquals("live-api-s.facebook.com", facebook.host)
        assertEquals(443, facebook.port)
        assertEquals("rtmp", facebook.app)
        assertEquals("fb_token_123", facebook.streamKey)
    }

    @Test
    fun testAmf0StringAndNumberRoundTrip() {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "connect")
        Amf0.writeNumber(baos, 1.0)
        Amf0.writeBoolean(baos, true)

        val bais = ByteArrayInputStream(baos.toByteArray())
        assertEquals("connect", Amf0.readValue(bais))
        assertEquals(1.0, Amf0.readValue(bais))
        assertEquals(true, Amf0.readValue(bais))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("00:05", VideoMetadataUtil.formatDuration(5000))
        assertEquals("01:23", VideoMetadataUtil.formatDuration(83000))
        assertEquals("01:02:03", VideoMetadataUtil.formatDuration(3723000))
    }
}
