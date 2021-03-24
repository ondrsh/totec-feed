/**
 * Created by ndrsh on 6/21/20
 */

package com.totec.trading.network.feeds

import com.totec.parser.json.FastJsonReader
import com.totec.trading.core.utils.logger
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.common.message.MessageInputStream
import org.eclipse.jetty.websocket.common.message.MessageReader
import java.io.*
import java.util.concurrent.CompletionStage

abstract class Websocket(private val address: String) : LiveFeed() {
	
	val client: WebSocketClient = WebSocketClient().apply { httpClient.connectTimeout = 5000 }
	lateinit var session: Session
	
	override fun connect(block: () -> Unit) {
		/*if (::client.isInitialized == false) {
			client = WebSocketClient()
			client.httpClient.connectTimeout = 5000
		}*/
		client.start()
		@Suppress("BlockingMethodInNonBlockingContext", "UNCHECKED_CAST")
		client.connect(this, java.net.URI.create(address)) as CompletionStage<Session>
		connectedCallback = block
		println("exiting the connected function")
	}
	
	override fun disconnect() {
		try {
			session.close()
		} catch (ex: Exception) {
			logger.warn("Websocket at $address failed to close session - probably was closed already")
		}
	}
	
	abstract fun processByteArray(byteArrayInputStream: ByteArrayInputStream)
	
	var mscount = 0L
	var nanos = arrayListOf<Long>()
	
/*	val field = BufferedReader::class.java.getDeclaredField("stream")
	
	init {
		field.isAccessible = true
	}*/
	
	abstract fun process(msgReader: Reader)
	
	@OnWebSocketMessage
	fun onMessage(msgReader: Reader) {
		// val stream = field.get(br) as MessageInputStream
		val start = System.nanoTime()
		// val byteArray = IOUtils.toByteArray(msg.buffered(8), "UTF-8")
		// processByteArray(byteArray)
		process(msgReader)
		msgReader.close()
		val end = System.nanoTime()
		nanos.add(end - start)
	}
	
/*	@OnWebSocketMessage
	fun onMessage(msg: String) {
		val start = System.nanoTime()
		processByteArray(ByteArrayInputStream(msg.toByteArray()))
		val end = System.nanoTime()
		nanos.add(end - start)
	}*/
	
	@OnWebSocketConnect
	fun onConnect(session: Session) {
		println("connected, setting session")
		this.session = session
		connectedCallback.invoke()
		println("finished executing block")
	}
	
	fun sendMessage(message: String) {
		session.remote.sendStringByFuture(message)
	}
	
}