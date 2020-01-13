package epmd

import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel

import io.ktor.network.selector.*

import kotlinx.coroutines.*

import java.net.InetSocketAddress
import java.nio.ByteBuffer

enum class EpmdProto(val opcode: Byte) {
    ALIVE2_REQ(120),
    ALIVE2_RESP(121),

    PORT_PREASE2_REQ(122),
    PORT2_RESP(119),

    NAMES_REQ(110),

    DUMP_REQ(100),
    KILL_REQ(107),
    STOP_REQ(115)
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
    val creation: UShort

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
}

fun main(args: Array<String>) {
    runBlocking {
        val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind(InetSocketAddress("127.0.0.1", 2323))
        println("Started echo telnet server at ${server.localAddress}")

        while (true) {
            val socket = server.accept()

            launch {
                println("Socket accepted: ${socket.remoteAddress}")

                val input = socket.openReadChannel()
                val output = socket.openWriteChannel(autoFlush = true)

                try {
                    while (true) {
                        val line = input.readUTF8Line(255)

                        println("${socket.remoteAddress}: $line")
                        output.writeFully(ByteBuffer.wrap("$line\r\n".toByteArray()))
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    socket.close()
                }
            }
        }
    }
}
