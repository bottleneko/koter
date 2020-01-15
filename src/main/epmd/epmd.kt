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
    val extra: ByteArray
    var creation: UShort

    init {
        val ns = name.split("@")
	      if (ns.size != 2) {
            // TODO: отдельный класс исключений
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
        extra = ByteArray(0)
        creation = 0u
    }


    fun compose_ALIVE2_Req(): ByteBuffer {
        // https://erlang.org/doc/apps/erts/erl_dist_protocol.html#register-a-node-in-epmd
        // Table 14.2
        return ByteBuffer.allocate(2 + 13 + name.length + extra.size)
            .putShort((13 + name.length + extra.size).toShort())

            .put(EpmdProto.ALIVE2_REQ.opcode)

            .putShort(this.port.toShort())

            .put(this.type.toByte())
            .put(this.protocol.toByte())
            .putShort(this.highVsn.toShort())
            .putShort(this.lowVsn.toShort())

            .putShort(this.name.length.toShort())
            .put(ByteBuffer.wrap(this.name.toByteArray()))

            .putShort(this.extra.size.toShort())
            .put(ByteBuffer.wrap(this.extra.toByteArray()))
    }

    fun compose_PLEASE_PORT2_Req(shortName: String): ByteBuffer {
        // https://erlang.org/doc/apps/erts/erl_dist_protocol.html#get-the-distribution-port-of-another-node
        // Table 14.4
        return ByteBuffer.allocate(2 + 1 + name.length)
            .putShort((1 + name.length).toShort())

            .put(EpmdProto.PORT_PREASE2_REQ.opcode)
            .put(ByteBuffer.wrap(shortName.toByteArray()))
    }

    fun resolveName(name: String): UShort {
        val ns = name.split("@")

        // TODO: проверка что соединение установлено
        val connection = Socket(ns[1], portEpmd.toInt())
        val reader: InputStream = connection.getInputStream()
        val writer: OutputStream = connection.getOutputStream()

        writer.write(compose_PLEASE_PORT2_Req(ns[0]).array())
        writer.flush()

        var buf = ByteArray(1024)
        reader.read(buf)

        var byteBuffer = ByteBuffer.wrap(buf)

        if(byteBuffer.get() != EpmdProto.PORT2_RESP.opcode) {
            // TODO: отдельный класс исключений
            throw Exception("Malformed EPMD reply")
        }

        val result = byteBuffer.get()

        if(result != 0u.toByte()) {
            // TODO: отдельный класс исключений
            throw Exception("EPMD cannot resolve name")
        }

        val port = byteBuffer.getShort().toUShort()

        return port
    }

    fun connect() {
        thread {
            while(true) {
                // FIXME: Не сработает, если EPMD слушает по другому loopback адресу
                // TODO: проверка что соединение установлено
                val connection = Socket("127.0.0.1", portEpmd.toInt())
                val reader: InputStream = connection.getInputStream()
                val writer: OutputStream = connection.getOutputStream()

                while(true) {
                    writer.write(compose_ALIVE2_Req().array())
                    writer.flush()

                    var byteBuffer = ByteBuffer.wrap(bufferedRead(4, reader))

                    if(byteBuffer.array().size != 4) {
                        // TODO: логгер на уровень debug
                        println("EPMD close connection")
                        break
                    }

                    if(byteBuffer.get() != EpmdProto.ALIVE2_RESP.opcode) {
                        // TODO: отдельный класс исключений
                        throw Exception("Malformed EPMD reply")
                    }

                    val result = byteBuffer.get().toUByte()
                    val creation = byteBuffer.getShort().toUShort()

                    if(result != 0u.toUByte()) {
                        // TODO: отдельный класс исключений
                        throw Exception("EPMD cannot allocate port")
                    }

                    if(creation == 0u.toUShort()) {
                        // TODO: отдельный класс исключений
                        throw Exception("Duplicate name ${name}")
                    }

                    this.creation = creation

                    // TODO: логгер на уровень debug
                    println("EPMD ALIVE2_RESP: ${result} ${creation}")
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    val epmd = Epmd("test3@localhost", 30000u, 4369u, true)
    epmd.connect()

    println(epmd.resolveName("test@localhost"))
}


fun bufferedRead(bytes: Int, input : InputStream): ByteArray {
    var buf = ByteArray(1024)
    tailrec fun bufferedRead(bytes: Int, buf: ByteArray): Int =
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
