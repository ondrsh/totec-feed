/**
 * Created by ndrsh on 7/22/20
 */

package com.totec.trading.network.feeds.coinbase

import com.dslplatform.json.DslJson
import com.dslplatform.json.JsonReader
import com.totec.trading.network.feeds.Websocket
import com.totec.trading.network.feeds.bitmex.BitmexWebsocket
import net.openhft.smoothie.SmoothieMap
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import java.io.ByteArrayInputStream

/*@WebSocket(maxTextMessageSize = 65536 * 1000)
class CoinbaseWebsocket : Websocket(address = "wss://ws-feed.pro.coinbase.com") {
	
	private val dsl = DslJson(DslJson.Settings<Any>().includeServiceLoader())
	private val reader = dsl.newReader()
	
	var instrumentsInitialized = false
	private val symsToIdsToEntries = SmoothieMap<String, SmoothieMap<Long, Double>>()
	private val temporaryBooks: HashMap<String, BitmexWebsocket.TemporaryBookState> = hashMapOf()
	
	override fun processByteArray(byteArrayInputStream: ByteArrayInputStream) {
		reader.process(byteArrayInputStream).processCoinbaseMessage()
	}
	
	private fun JsonReader<*>.processCoinbaseMessage() {
		startObject()
		nextToken // "
		val field1 = readString()
		nextToken // :
		when (field1) {
			"success" -> {
			}
			"table"   -> processTable()
		}
		val x = 2
		val y = x + 4
	}
	
	
	suspend fun subscribe() {
		val request = mapper.createObjectNode()
		request.put("type", "subscribe")
		request.replace("product_ids", getSubscriptionArray())
		request.replace("channels", mapper.createArrayNode().add("full"))
		sendMessage(request.toString())
	}
	
	fun getSubscriptionArray(): ArrayNode {
		val subArray = mapper.createArrayNode()
		
		for (symbol: String in partition.exchange.instruments) {
			subArray.add(symbol)
		}
		
		return subArray
	}
}*/
