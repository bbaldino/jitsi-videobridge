/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.videobridge.datachannel

import org.jitsi.utils.logging2.Logger
import org.jitsi.utils.logging2.createChildLogger
import org.jitsi.videobridge.datachannel.protocol.DataChannelMessage
import org.jitsi.videobridge.sctp.Ppid
import org.jitsi.videobridge.sctp.Sid
import java.nio.ByteBuffer

typealias OutgoingDataChannelDataSender = (ByteBuffer, Sid, Ppid) -> Unit
typealias IncomingDataChannelTransportDataHandler = (DataChannelMessage) -> Unit

class DataChannelTransportStandalone(
    parentLogger: Logger
) {
    private val logger = createChildLogger(parentLogger)

    private val dataChannelStack = DataChannelStack(
        DataChannelStack.DataChannelDataSender { data, sid, ppid ->
            send(data, sid, ppid.toLong())
        },
        logger
    ).apply {
        //TODO: instead of passing this datachannel to the event handler,
        // we'll save it internally and use it in 'send'
        onDataChannelStackEvents { dataChannel ->
            logger.info("Remote side opened a data channel.")
            eventHandler?.connected(dataChannel)
        }
    }

    @JvmField
    var dataSender: OutgoingDataChannelDataSender? = null

    //TODO: this won't be used for now, as the endpoint message transport
    // currently takes the datachannel directly and sets itself as the handler.
    // we should change the ep msg transport to work through this transport
    // layer instead of the datachannel directly
    @JvmField
    var incomingDataHandler: IncomingDataChannelTransportDataHandler? = null

    @JvmField
    var eventHandler: DataChannelTransportEventHandler? = null

    // Open the data channel locally instead of waiting for the remote side
    fun openDataChannel() {
        //TODO
    }

    // TODO: this will never be called externally, because all sending right now
    // is happening via the DataChannel object directly (which uses this method).
    // When we change ep msg transport to use this layer instead of the datachannel,
    // it will use this method which will then send "through" the DataChannel
    // instance.
    private fun send(data: ByteBuffer, sid: Int, ppid: Long): Int {
        return dataSender?.let {
            it(data, sid, ppid)
            1
        } ?: 0
    }

    fun dataReceived(data: ByteArray, off: Int, length: Int, sid: Int, ppid: Long) {
        dataChannelStack.onIncomingDataChannelPacket(ByteBuffer.wrap(data, off, length), sid, ppid.toInt())
    }

    interface DataChannelTransportEventHandler {
        fun connected(channel: DataChannel)
    }
}