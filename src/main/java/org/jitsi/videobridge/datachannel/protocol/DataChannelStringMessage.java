package org.jitsi.videobridge.datachannel.protocol;

import java.nio.*;
import java.nio.charset.*;

public class DataChannelStringMessage extends DataChannelMessage
{
    public final String data;

    public DataChannelStringMessage(String data)
    {
        this.data = data;
    }

    public static DataChannelStringMessage parse(byte[] data)
    {
        String stringData = new String(data, StandardCharsets.UTF_8);
        return new DataChannelStringMessage(stringData);
    }

    @Override
    public ByteBuffer getBuffer()
    {
        return ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8));
    }
}
