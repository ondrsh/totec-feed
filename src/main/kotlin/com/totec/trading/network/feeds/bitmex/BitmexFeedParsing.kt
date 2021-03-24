/**
 * Created by ndrsh on 7/6/20
 */

package com.totec.trading.network.feeds.bitmex

import com.dslplatform.json.JsonReader
import com.dslplatform.json.JsonWriter
import com.dslplatform.json.NumberConverter
import com.totec.trading.core.instrument.book.BookEntry
import com.totec.trading.core.instrument.book.Trade
import com.totec.trading.core.instrument.book.ops.BookOp
import java.time.Instant


// : --> 58
// " --> 34
// , --> 44
/*

object EntryBinder : JsonReader.BindObject<BookEntry> {
	override fun bind(reader: JsonReader<Any>, instance: BookEntry): BookEntry {
		BookOp
		return instance
	}
}

object TradeBinder : JsonReader.BindObject<Trade> {
	override fun bind(reader: JsonReader<*>, instance: Trade): Trade {
		do {
			val startString = reader.nextToken
			val attribute = reader.readString()
			// val comma = reader.nextToken // :
			val nextToken = reader.handle(attribute, instance)
		} while (nextToken != '}'.toByte())
		
		return instance
	}
	
	private fun JsonReader<*>.handle(attribute: String, instance: Trade): Byte {
		val colon = nextToken
		when (attribute) {
			"timestamp" -> {
				nextToken // start
				instance.timestamp = TimestampConverter.JSON_READER.read(this)!!
				return nextToken
			}
			"price"     -> {
				nextToken // start
				instance.price = NumberConverter.deserializeDouble(this)
				val n = nextToken
				return n
			}
			"size"      -> {
				nextToken // start
				instance.amount = NumberConverter.deserializeDouble(this)
				val n = nextToken
				return n
			}
			else        -> {
				nextToken // start
				val s = skip()
				return s
				// while (nextToken != 34.toByte()) {}
			}
		}
	}
	
	*/
/*	private fun JsonReader<*>.timestampConvert(): Long {
			val comma2 = nextToken
			val s: String = readString()
			return Instant.parse(s).toEpochMilli()
		}*//*

}

object TimestampConverter {
	
	@JvmStatic
	val JSON_READER: JsonReader.ReadObject<Long?> = JsonReader.ReadObject<Long?> { reader: JsonReader<Any> ->
		val s: String = reader.readString()
		reader.toEpochMilli(s)
	}
	val JSON_WRITER: JsonWriter.WriteObject<Long?> = JsonWriter.WriteObject<Long?> { writer, value -> TODO("Not yet implemented") }
	
	fun JsonReader<Any>.toEpochMilli(s: String) = Instant.parse(s).toEpochMilli()
}

*/
