package epmd

import java.net.InetSocketAddress

import java.nio.ByteBuffer
import java.nio.ByteOrder

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

import java.util.Scanner

import kotlin.concurrent.thread

enum class EpmdProto(val opcode: Byte) {
    ALIVE2_REQ(120),
    ALIVE2_RESP(121),

    PORT_PREASE2_REQ(122),
    PORT2_RESP(119),

    NAMES_REQ(110),

    DUMP_REQ(100),
    KILL_REQ(107),
    STOP_REQ(115);

    companion object {
        private val map = EpmdProto.values().associateBy(EpmdProto::opcode)
        fun fromOpcode(opcode: Byte) = map[opcode]
    }
}

@UseExperimental(kotlin.ExperimentalUnsignedTypes::class)
class Epmd(name: String, listenPort: UShort, epmdPort: UShort, hidden: Boolean = false) {
    val fullName: String
    val name: String
    val domain: String

    val port: UShort

    val portEpmd: UShort

    // http://erlang.org/doc/reference_manual/distributed.html (section 13.5)
    val type: UByte

    val protocol: UByte
    val highVsn: UShort
    val lowVsn: UShort
    val extra: Array<Byte>
    var creation: UShort

    init {
        val ns = name.split("@")
	      if (ns.size != 2) {
		        throw Exception("FQDN for node name is required (example: node@hostname)")
	      }

        fullName = name
        this.name = ns[0]
        domain = ns[1]

        port = listenPort

        portEpmd = epmdPort

        type = if(hidden) 72u else 77u

        protocol = 0u
        highVsn = 5u
        lowVsn = 5u
        extra = arrayOf()
        creation = 0u
    }


    fun compose_ALIVE2_Req(): ByteBuffer {
        // https://erlang.org/doc/apps/erts/erl_dist_protocol.html#register-a-node-in-epmd
        // Table 14.2
        val request = ByteBuffer.allocate(2 + 13 + name.length + extra.size)
            .putShort((13 + name.length + extra.size).toShort())
            .put(EpmdProto.ALIVE2_REQ.opcode.toByte())

            .putShort(this.port.toShort())

            .put(this.type.toByte())
            .put(this.protocol.toByte())
            .putShort(this.highVsn.toShort())
            .putShort(this.lowVsn.toShort())

            .putShort(this.name.length.toShort())
            .put(ByteBuffer.wrap(this.name.toByteArray()))

            .putShort(this.extra.size.toShort())
            .put(ByteBuffer.wrap(this.extra.toByteArray()))

        return request
    }

    fun connect() {

    }
}

fun main(args: Array<String>) {
    val epmd = Epmd("test3@localhost", 30000u, 4369u, true)

    val connection = Socket("127.0.0.1", 4369)
    val reader: InputStream = connection.getInputStream()
    val writer: OutputStream = connection.getOutputStream()

    writer.write(epmd.compose_ALIVE2_Req().array())
    writer.flush()

    println("1")

    var byteBuffer = ByteBuffer.wrap(bufferedRead(4, reader))
    val aliveResp = byteBuffer.get()
    val result = byteBuffer.get().toUByte()
    val creation = byteBuffer.getShort().toUShort()

    if(aliveResp != EpmdProto.ALIVE2_RESP.opcode) {
        throw Exception("Malformed EPMD reply")
    }

    if(result != 0u.toUByte()) {
        throw Exception("EPMD cannot allocate port")
    }

    if(creation == 0u.toUShort()) {
        throw Exception("Duplicate name ${epmd.name}")
    }

    epmd.creation = creation

    println("${EpmdProto.fromOpcode(aliveResp)} ${result} ${creation}")
}


fun bufferedRead(bytes: Int, input : InputStream): ByteArray {
    var buf = ByteArray(1024)
    fun bufferedRead(bytes: Int, buf: ByteArray): Int =
        when {
            bytes < 0  -> -1
            bytes == 0 -> 0
            else       -> {
                val bytesReaded = input.read(buf)
                bufferedRead(bytes - bytesReaded, buf)
            }
        }

    if (bufferedRead(bytes, buf) == 0) {
        return ByteArray(bytes, { buf.get(it) })
    } else {
        return ByteArray(0)
    }
}
