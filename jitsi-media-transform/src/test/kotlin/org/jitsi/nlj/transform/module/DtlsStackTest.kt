package org.jitsi.nlj.transform.module

import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
import org.bouncycastle.crypto.tls.*
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.jitsi.nlj.PacketInfo
import org.jitsi.nlj.dtls.DtlsClientStack
import org.jitsi.nlj.dtls.TlsClientImpl
import org.jitsi.nlj.transform.node.Node
import org.jitsi.nlj.transform.node.incoming.DtlsReceiver
import org.jitsi.nlj.transform.node.outgoing.DtlsSender
import org.jitsi.rtp.UnparsedPacket
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread


data class PacketData(val buf: ByteArray, val off: Int, val length: Int)

class FakeTransport : DatagramTransport {
    val incomingQueue = LinkedBlockingQueue<PacketData>()
    var sendFunc: (ByteArray, Int, Int) -> Unit = { _, _, _ -> Unit}
    override fun receive(buf: ByteArray, off: Int, length: Int, waitMillis: Int): Int {
        val pData: PacketData? = incomingQueue.poll(waitMillis.toLong(), TimeUnit.MILLISECONDS)
        pData?.let {
            System.arraycopy(it.buf, it.off, buf, off, Math.min(length, it.length))
        }
        return pData?.length ?: -1
    }

    override fun send(buf: ByteArray, off: Int, length: Int) {
        sendFunc(buf, off, length)
    }

    override fun close() {
    }

    override fun getReceiveLimit(): Int = 1350

    override fun getSendLimit(): Int = 1350
}

fun generateCert(keyPair: KeyPair): Certificate {

    val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA")
    val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
    val res = PrivateKeyFactory.createKey(keyPair.private.encoded)
    val sigGen2 = BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(res)

    val startDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    val endDate = Date(System.currentTimeMillis() + 365 * 86400000L)
    val v1CertGen: X509v1CertificateBuilder = JcaX509v1CertificateBuilder(
          X500Principal("CN=Test"),
          BigInteger.ONE,
          startDate, endDate,
          X500Principal("CN=Test"),
          keyPair.public);

    val certHolder: X509CertificateHolder = v1CertGen.build(sigGen2);
    return Certificate(arrayOf(certHolder.toASN1Structure()))
}

class TlsServerImpl : DefaultTlsServer() {
    val keyPair: KeyPair
    val certificate: Certificate
    private val mki = TlsUtils.EMPTY_BYTES
    private val srtpProtectionProfiles = intArrayOf(
        SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80,
        SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_32
    )
    init {
        val keypairGen = KeyPairGenerator.getInstance("RSA")
        keypairGen.initialize(1024, SecureRandom())
        keyPair = keypairGen.generateKeyPair()
        certificate = generateCert(keyPair)
    }
    override fun getMinimumVersion(): ProtocolVersion = ProtocolVersion.DTLSv10
    override fun getMaximumVersion(): ProtocolVersion = ProtocolVersion.DTLSv10
    override fun getRSASignerCredentials(): TlsSignerCredentials {
        return DefaultTlsSignerCredentials(context, certificate, PrivateKeyFactory.createKey(keyPair.private.encoded))
    }

    override fun getServerExtensions(): Hashtable<*, *> {
        var serverExtensions = super.getServerExtensions();
        if (TlsSRTPUtils.getUseSRTPExtension(serverExtensions) == null) {
            if (serverExtensions == null) {
                serverExtensions = Hashtable<Int, ByteArray>()
            }

            TlsSRTPUtils.addUseSRTPExtension(
                serverExtensions,
                UseSRTPData(srtpProtectionProfiles, mki)
            )
        }

        return serverExtensions
    }
}

// A simple, somewhat hacky test just to verify the handshake can complete and we can send data
internal class DtlsStackTest : ShouldSpec() {
    init {
        val dtls = DtlsClientStack()
        val receiver = DtlsReceiver(dtls)
        val sender = DtlsSender(dtls)

        val serverTransport = FakeTransport()
        val dtlsServer = TlsServerImpl()
        val serverProtocol = DTLSServerProtocol(SecureRandom())

        serverTransport.sendFunc = { buf, off, len ->
            receiver.processPackets(listOf(PacketInfo(UnparsedPacket(ByteBuffer.wrap(buf, off, len)))))
        }
        sender.attach(object : Node("sender network") {
            override fun doProcessPackets(p: List<PacketInfo>) {
                p.forEach {
                    serverTransport.incomingQueue.add(PacketData(
                            it.packet.getBuffer().array(),
                            it.packet.getBuffer().arrayOffset(),
                            it.packet.getBuffer().limit()))
                }
            }
        })

        val receivedDataFuture = CompletableFuture<String>()
        var serverRunning = true
        val serverThread = thread {
            val serverDtlsTransport = serverProtocol.accept(dtlsServer, serverTransport)
            val buf = ByteArray(1500)
            while (serverRunning) {
                val len = serverDtlsTransport.receive(buf, 0, 1500, 100)
                if (len > 0) {
                    val receivedStr = String(buf, 0, len)
                    receivedDataFuture.complete(receivedStr)
                }
            }
        }

        dtls.connect(TlsClientImpl())
        val message = "Hello, world"
        dtls.sendDtlsAppData(PacketInfo(UnparsedPacket(ByteBuffer.wrap(message.toByteArray()))))
        receivedDataFuture.get() shouldBe message

        serverRunning = false
        serverThread.join()
    }
}
