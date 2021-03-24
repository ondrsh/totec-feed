/**
 * Created by ndrsh on 6/22/20
 */

package com.totec.trading.network.feeds.bitmex

import com.dslplatform.json.DslJson
import com.dslplatform.json.JsonReader
import com.totec.parser.json.FastJsonReader
import com.totec.trading.core.exchanges.createAndAddInstrument
import com.totec.trading.core.instrument.Instrument
import com.totec.trading.core.instrument.Liquidation
import com.totec.trading.core.instrument.book.Book
import com.totec.trading.core.instrument.book.BookEntry
import com.totec.trading.core.instrument.book.Side
import com.totec.trading.core.instrument.book.Trade
import com.totec.trading.core.instrument.book.bookside.BookSide
import com.totec.trading.core.instrument.book.ops.BookOp
import com.totec.trading.core.instrument.book.ops.OpType
import com.totec.trading.core.instrument.book.ops.OpType.*
import com.totec.trading.core.utils.dsljson.fillObjectOrArray
import com.totec.trading.core.utils.dsljson.readDouble
import com.totec.trading.core.utils.dsljson.readLong
import com.totec.trading.core.utils.dsljson.readTimestamp
import com.totec.trading.core.utils.logger
import com.totec.trading.network.feeds.Websocket
import com.totec.trading.network.rest.exchanges.bitmex.BitmexAuth
import net.openhft.smoothie.SmoothieMap
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import java.io.ByteArrayInputStream
import java.io.Reader
import java.time.Instant


/**
 * Bitmex sends timestamps only for trades and instruments (partials as well as updates),
 * but neither for book snapshots nor for book updates.
 */
@WebSocket(maxTextMessageSize = 65536*10_000)
open class BitmexWebsocket() : Websocket(address = "wss://www.bitmex.com/realtime") {
	
	val dsl = DslJson(DslJson.Settings<Any>().includeServiceLoader())
	val reader = dsl.newReader()
	
	var instrumentsInitialized = false
	var bookPartialsArrived = false
	val symbolsAffected = mutableSetOf<String>()
	val symsToIdsToEntries = SmoothieMap<String, SmoothieMap<Long, Double>>()
	val temporaryBooks: HashMap<String, TemporaryBookState> = hashMapOf()
	
	override fun reset() {
		TODO("Not yet implemented")
	}
	
	fun sendAuth() = sendMessage(getAuthMessage())
	
	fun sendSubscription(with: Boolean = false) = sendMessage(getSubscriptionMessage(with))
	
	val fr = FastJsonReader(Reader.nullReader())
	
	override fun processByteArray(byteArrayInputStream: ByteArrayInputStream) {
		reader.process(byteArrayInputStream).processBitmexMessage()
	}
	
	var count = 0
	override fun process(msgReader: Reader) {
		fr.changeReader(msgReader)
		with(fr) {
			count++
			startObject()
			when (readProperty()) {
				"table" -> processTable()
				else    -> skipBeforeObjectEnd()
			}
			try {
				endObject()
			} catch (e: Exception) {
				println()
			}
		}
		for (symbol in symbolsAffected) partition.exchange.instruments[symbol]?.flush(System.currentTimeMillis())
		symbolsAffected.clear()
	}
	
	fun JsonReader<*>.processBitmexMessage() {
		startObject()
		nextToken // "
		val field1 = readString()
		nextToken // :
		when (field1) {
			"success" -> {
			}
			"table"   -> processTable()
		}
		for (symbol in symbolsAffected) partition.exchange.instruments[symbol]?.flush(System.currentTimeMillis())
		symbolsAffected.clear()
	}
	
	fun FastJsonReader.processTable() {
		when (readString()) {
			"trade"       -> processTradeTable()
			"orderBookL2" -> processL2Table()
			"instrument"  -> processInstrumentTable()
			"liquidation" -> processLiquidationTable()
			else          -> skipBeforeObjectEnd()
		}
	}
	
	fun JsonReader<*>.processTable() {
		nextToken // :
		when (readString()) {
			"trade"       -> processTradeTable()
			"orderBookL2" -> processL2Table()
			"instrument"  -> processInstrumentTable()
		}
		nextToken // ,
	}
	
	/**
	 * Action can be 'partial' or 'insert' at Bitmex, but we only use the live ('insert') trades and ignore the
	 * partials (snapshots) that we receive after subscribing.
	 */
	fun FastJsonReader.processTradeTable() {
		getAction()?.let {
			when (it) {
				"insert" -> {
					comma()
					jumpToProperty("data")
					processTradeInserts()
				}
				else     -> {
					if (it != "partial") logger.warn("Bitmex Websocket - found Trade action '$it', cannot process it")
					skipBeforeObjectEnd()
				}
			}
		}
	}
	
	/**
	 * Action can be 'partial' or 'insert' at Bitmex, but we only use the live ('insert') trades and ignore the
	 * partials (snapshots) that we receive after subscribing.
	 */
	fun JsonReader<*>.processTradeTable() {
		when (getAction()) {
			"insert" -> {
				comma()
				startAttribute("data")
				processTradeInsert()
			}
		}
	}
	
	fun FastJsonReader.processL2Table() {
		getAction()?.let {
			when (it) {
				"partial" -> {
					comma()
					jumpToProperty("data")
					processL2Partials()
					bookPartialsArrived = true
				}
				"insert"  -> {
					comma()
					jumpToProperty("data")
					processL2Inserts()
				}
				"update"  -> {
					comma()
					jumpToProperty("data")
					processL2Updates()
				}
				"delete"  -> {
					comma()
					jumpToProperty("data")
					processL2Deletes()
				}
				else      -> logger.warn("Bitmex Websocket - found L2 action '$it', cannot process it")
			}
		}
	}
	
	fun JsonReader<*>.processL2Table() {
		when (getAction()) {
			"partial" -> {
				comma()
				startAttribute("data")
				processL2Partials()
				bookPartialsArrived = true
			}
			"insert"  -> {
				comma()
				startAttribute("data")
				processL2Inserts()
			}
			"update"  -> {
				comma()
				startAttribute("data")
				processL2Updates()
			}
			"delete"  -> {
				comma()
				startAttribute("data")
				processL2Deletes()
			}
		}
	}
	
	fun FastJsonReader.processInstrumentTable() {
		when (getAction()) {
			"partial" -> {
				comma()
				jumpToProperty("data")
				processInstrumentPartials()
			}
			"update"  -> {
				comma()
				jumpToProperty("data")
				processInstrumentUpdates()
			}
		}
	}
	
	fun JsonReader<*>.processInstrumentTable() {
		when (getAction()) {
			"partial" -> {
				comma()
				startAttribute("data")
				processInstrumentPartials()
			}
			"update"  -> {
				comma()
				startAttribute("data")
				processInstrumentUpdates()
			}
		}
	}
	
	// {"table":"liquidation","action":"insert","data":[{"orderID":"f5651529-90f6-b8e2-0089-a494b81328b7","symbol":"ETHUSD","side":"Buy","price":475.1,"leavesQty":1}]}
	fun FastJsonReader.processLiquidationTable() {
		val action = getAction()
		comma()
		jumpToProperty("data")
		when (action) {
			// this is not a mistake. just to the same with partials. check in engines if they are different
			"partial", "insert" -> processLiquidations(INSERT)
			"update"            -> processLiquidations(CHANGE)
			"delete"            -> processLiquidations(DELETE)
		}
	}
	
	// {"table":"liquidation","action":"insert","data":[{"orderID":"f5651529-90f6-b8e2-0089-a494b81328b7","symbol":"ETHUSD","side":"Buy","price":475.1,"leavesQty":1}]}
	fun FastJsonReader.processLiquidations(opType: OpType) {
		startArray()
		// this can just be an empty array when reading partials, so we have to read is this way
		while (readChar() != ']') {
			if (last != ',') backTrack = true
			processLiquidation(opType)
		}
		checkArrayEnd()
	}
	
	fun FastJsonReader.processLiquidation(opType: OpType) {
		val liquidation = readLiquidation(opType)
		val instrument = partition.exchange.instruments[liquidation.symbol]
		if (instrument != null) {
			when (opType) {
				INSERT, CHANGE -> instrument.processLiquidation(liquidation)
				DELETE         -> instrument.processLiquidation(liquidation)
			}
			symbolsAffected.add(liquidation.symbol)
		}
	}
	
	private fun FastJsonReader.readLiquidation(opType: OpType): Liquidation {
		startObject()
		val orderID = readPropertyString("orderID")!!; comma()
		
		val liquidation = when (opType) {
			INSERT -> {
				val symbol = getSymbol(); comma()
				val side = getSide(); comma()
				val price = getPrice(); comma()
				val amount = getAmount()
				Liquidation(orderID, price, amount, side, symbol, System.currentTimeMillis())
			}
			DELETE -> {
				val symbol = getSymbol()
				Liquidation(orderID, Double.NaN, Double.NaN, Side.Buy, "", System.currentTimeMillis())
			}
			CHANGE -> {
				var (price, leavesQty) = Double.NaN to Double.NaN
				var next = readProperty()
				while (next != "symbol") {
					when (next) {
						"price" -> price = readDouble()
						"leavesQty" -> leavesQty = readDouble()
					}
					comma()
					next = readProperty()
				}
				val symbol = readString()
				Liquidation(orderID, price, leavesQty, Side.Buy, symbol, System.currentTimeMillis())
			}
		}
		
		endObject()
		return liquidation
	}
	
	fun FastJsonReader.processTradeInserts() {
		startArray()
		do {
			processIndividualTradeInsert()
		} while (readChar() == ',')
		checkArrayEnd()
	}
	
	fun JsonReader<*>.processTradeInsert() {
		startArray()
		do {
			processIndividualTradeInsert()
		} while (nextToken == ','.toByte())
	}
	
	
	fun FastJsonReader.processIndividualTradeInsert() {
		startObject()
		val isTimestamp = System.currentTimeMillis()
		val shouldTimestamp = getTimestamp(); comma()
		val lag = isTimestamp - shouldTimestamp
		val symbol = getSymbol(); comma()
		val instrument = partition.exchange.instruments[symbol]
		if (instrument == null) {
			jumpAfterNext('}')
			return
		}
		val side = getSide(); comma()
		val amount = getAmount(); comma()
		val price = getPrice()
		jumpAfterNext('}')
		
		val trade = Trade.getNext(price, amount, side, isTimestamp)
		instrument.processTrade(trade)
		instrument.addTradeLag(lag, isTimestamp)
		symbolsAffected.add(symbol)
	}
	
	fun JsonReader<*>.processIndividualTradeInsert() {
		startObject()
		val isTimestamp = System.currentTimeMillis()
		val shouldTimestamp = getTimestamp(); comma()
		val lag = isTimestamp - shouldTimestamp
		val symbol = getSymbol(); comma()
		val instrument = partition.exchange.instruments[symbol]
		if (instrument == null) {
			fillObjectOrArray('{')
			return
		}
		val side = getSide(); comma()
		val amount = getAmount(); comma()
		val price = getPrice()
		fillObjectOrArray('{')
		
		val trade = Trade.getNext(price, amount, side, isTimestamp)
		instrument.processTrade(trade)
		instrument.addTradeLag(lag, isTimestamp)
		symbolsAffected.add(symbol)
	}
	
	fun FastJsonReader.processL2Partials() {
		startArray()
		val books = HashMap<String, Book>()
		do {
			fillL2PartialIndividual(books)
		} while (readChar() == ',')
		checkArrayEnd()
		
		if (instrumentsInitialized == false) {
			books.forEach { temporaryBooks[it.key] = TemporaryBookState(it.value) }
		} else {
			books.sendToInstruments()
		}
	}
	
	fun JsonReader<*>.processL2Partials() {
		startArray()
		val books = HashMap<String, Book>()
		do {
			fillL2PartialIndividual(books)
		} while (nextToken == ','.toByte())
		
		if (instrumentsInitialized == false) {
			books.forEach { temporaryBooks[it.key] = TemporaryBookState(it.value) }
		} else {
			books.sendToInstruments()
		}
	}
	
	fun FastJsonReader.processL2Inserts() {
		startArray()
		do {
			processInsertOp()
		} while (readChar() == ',')
		checkArrayEnd()
	}
	
	fun JsonReader<*>.processL2Inserts() {
		startArray()
		do {
			processInsertOp()
		} while (nextToken == ','.toByte())
		checkArrayEnd()
	}
	
	fun FastJsonReader.processL2Updates() {
		if (bookPartialsArrived == false) return
		startArray()
		do {
			processChangeOp()
		} while (readChar() == ',')
		checkArrayEnd()
	}
	
	fun JsonReader<*>.processL2Updates() {
		if (bookPartialsArrived == false) return
		startArray()
		do {
			processChangeOp()
		} while (nextToken == ','.toByte())
		checkArrayEnd()
	}
	
	fun FastJsonReader.processL2Deletes() {
		if (bookPartialsArrived == false) return
		startArray()
		do {
			processDeleteOp()
		} while (readChar() == ',')
		checkArrayEnd()
	}
	
	fun JsonReader<*>.processL2Deletes() {
		if (bookPartialsArrived == false) return
		startArray()
		do {
			processDeleteOp()
		} while (nextToken == ','.toByte())
	}
	
	fun FastJsonReader.processInsertOp() {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide(); comma()
		val amount = getAmount(); comma()
		val price = getPrice()
		endObject()
		
		val idMap = symsToIdsToEntries.getInnerMapOrCreate(symbol)
		idMap[id] = price
		val insertOp = BookOp.getInsert(price, amount, System.currentTimeMillis())
		processOp(insertOp, side, symbol)
	}
	
	fun JsonReader<*>.processInsertOp() {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide(); comma()
		val amount = getAmount(); comma()
		val price = getPrice()
		endObject()
		
		val idMap = symsToIdsToEntries.getInnerMapOrCreate(symbol)
		idMap[id] = price
		val insertOp = BookOp.getInsert(price, amount, System.currentTimeMillis())
		processOp(insertOp, side, symbol)
	}
	
	fun FastJsonReader.processChangeOp() {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide(); comma()
		val amount = getAmount()
		endObject()
		
		val price = symsToIdsToEntries[symbol]!![id]!!
		val changeByInsertOp = BookOp.getInsert(price, amount, System.currentTimeMillis())
		processOp(changeByInsertOp, side, symbol)
	}
	
	fun JsonReader<*>.processChangeOp() {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide(); comma()
		val amount = getAmount()
		endObject()
		
		val price = symsToIdsToEntries[symbol]!![id]!!
		val changeByInsertOp = BookOp.getInsert(price, amount, System.currentTimeMillis())
		processOp(changeByInsertOp, side, symbol)
	}
	
	fun FastJsonReader.processDeleteOp() {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide()
		endObject()
		
		val idMap = symsToIdsToEntries[symbol]
		val price = idMap!![id]!!
		idMap.remove(id)
		val deleteOp = BookOp.getDelete(price, System.currentTimeMillis())
		processOp(deleteOp, side, symbol)
	}
	
	fun JsonReader<*>.processDeleteOp() {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide()
		endObject()
		
		val idMap = symsToIdsToEntries[symbol]
		val price = idMap!![id]!!
		idMap.remove(id)
		val deleteOp = BookOp.getDelete(price, System.currentTimeMillis())
		processOp(deleteOp, side, symbol)
	}
	
	fun processOp(op: BookOp, side: Side, symbol: String) {
		if (instrumentsInitialized) {
			val instrument = partition.exchange.instruments[symbol]
			if (instrument != null) {
				when (op.type) {
					OpType.INSERT -> instrument.processInsert(op, side)
					OpType.DELETE -> instrument.processDelete(op, side)
					else          -> throw RuntimeException() // there cannot be changes --> they are all inserts
				}
				symbolsAffected.add(symbol)
			}
		} else {
			val tempBook =
				(temporaryBooks[symbol] ?: throw RuntimeException("couldn't add L2 Op to temporary book because temporary book for $symbol does not exist"))
			when (side) {
				Side.Buy  -> tempBook.bidsOps.add(op)
				Side.Sell -> tempBook.asksOps.add(op)
			}
		}
	}
	
	fun FastJsonReader.fillL2PartialIndividual(books: HashMap<String, Book>) {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide(); comma()
		val amount = getAmount(); comma()
		val price = getPrice()
		endObject()
		
		val book = books[symbol] ?: partition.exchange.instrumentComponent.book().also {
			books[symbol] = it
			it.bids.lastUpdated = System.currentTimeMillis()
			it.asks.lastUpdated = System.currentTimeMillis()
		}
		val idMap = symsToIdsToEntries.getInnerMapOrCreate(symbol)
		idMap[id] = price
		val entry = BookEntry.getNext(price, amount, System.currentTimeMillis())
		book[side].set.add(entry)
		book[side].map[entry.price] = entry
	}
	
	fun JsonReader<*>.fillL2PartialIndividual(books: HashMap<String, Book>) {
		startObject()
		val symbol = getSymbol(); comma()
		val id = getId(); comma()
		val side = getSide(); comma()
		val amount = getAmount(); comma()
		val price = getPrice()
		endObject()
		
		val book = books[symbol] ?: partition.exchange.instrumentComponent.book().also {
			books[symbol] = it
			it.bids.lastUpdated = System.currentTimeMillis()
			it.asks.lastUpdated = System.currentTimeMillis()
		}
		val idMap = symsToIdsToEntries.getInnerMapOrCreate(symbol)
		idMap[id] = price
		val entry = BookEntry.getNext(price, amount, System.currentTimeMillis())
		book[side].set.add(entry)
		book[side].map[entry.price] = entry
	}
	
	// 'partial' basically means 'snapshot' in Bitmex language... confusing
	fun FastJsonReader.processInstrumentPartials() {
		startArray()
		val infos = with(partition.exchange.infoDeserializer) { readSet() }
		if (infos != null) {
			for (partialInfo in infos) {
				val instrument = partition.exchange.instruments[partialInfo.symbol]
				
				// instrument already exists
				if (instrument != null) {
					instrument.addInstrumentLag(partialInfo.calculateLag(), System.currentTimeMillis())
					instrument.replaceInfo(partialInfo)
					instrument.flush(System.currentTimeMillis())
				}
				// instrument gets created
				else {
					val newInstrument = partition.exchange.createAndAddInstrument(partialInfo)
					newInstrument.addInstrumentLag(partialInfo.calculateLag(), System.currentTimeMillis())
					newInstrument.flush(System.currentTimeMillis())
					val tempBook = temporaryBooks[newInstrument.info.symbol]
					if (tempBook != null) {
						tempBook.processAtInstrument(newInstrument)
						newInstrument.flush(System.currentTimeMillis())
						temporaryBooks.remove(newInstrument.info.symbol)
					}
				}
			}
		}
		instrumentsInitialized = true
	}
	
	// 'partial' basically means 'snapshot' in Bitmex language... confusing
	fun JsonReader<*>.processInstrumentPartials() {
		startArray()
		val infos = with(partition.exchange.instrumentInfoDeserializer) {
			readSet()
		}
		if (infos != null) {
			for (partialInfo in infos) {
				val instrument = partition.exchange.instruments[partialInfo.symbol]
				
				// instrument already exists
				if (instrument != null) {
					instrument.addInstrumentLag(partialInfo.calculateLag(), System.currentTimeMillis())
					instrument.replaceInfo(partialInfo)
					instrument.flush(System.currentTimeMillis())
				}
				// instrument gets created
				else {
					val newInstrument = partition.exchange.createAndAddInstrument(partialInfo)
					newInstrument.addInstrumentLag(partialInfo.calculateLag(), System.currentTimeMillis())
					newInstrument.flush(System.currentTimeMillis())
					val tempBook = temporaryBooks[newInstrument.info.symbol]
					if (tempBook != null) {
						tempBook.processAtInstrument(newInstrument)
						newInstrument.flush(System.currentTimeMillis())
						temporaryBooks.remove(newInstrument.info.symbol)
					}
				}
			}
		}
		instrumentsInitialized = true
	}
	
	fun FastJsonReader.processInstrumentUpdates() {
		startArray()
		val infos = with(partition.exchange.infoDeserializer) { readSet() }
		if (infos != null) {
			for (parsedInfo in infos) {
				val instrument = partition.exchange.instruments[parsedInfo.symbol]
				instrument?.apply {
					addInstrumentLag(parsedInfo.calculateLag(), System.currentTimeMillis())
					updateInfo(parsedInfo)
					flush(System.currentTimeMillis())
				}
			}
		}
	}
	
	
	fun JsonReader<*>.processInstrumentUpdates() {
		startArray()
		val infos = with(partition.exchange.instrumentInfoDeserializer) { readSet() }
		if (infos != null) {
			for (parsedInfo in infos) {
				val instrument = partition.exchange.instruments[parsedInfo.symbol]
				instrument?.apply {
					addInstrumentLag(parsedInfo.calculateLag(), System.currentTimeMillis())
					updateInfo(parsedInfo)
					flush(System.currentTimeMillis())
				}
			}
		}
	}
	
	fun FastJsonReader.getTimestamp(): Long {
		readProperty() // "timestamp"
		return readTimestamp()
	}
	
	fun JsonReader<*>.getTimestamp(): Long {
		nextToken // "
		fillName()
		nextToken // start timestamp
		return readTimestamp()
	}
	
	fun FastJsonReader.getSymbol() = readPropertyString("symbol")!!
	
	fun JsonReader<*>.getSymbol(): String {
		nextToken // "
		fillName()
		nextToken // start symbol
		return readString()
	}
	
	fun FastJsonReader.getId(): Long {
		readProperty()
		return readLong()
	}
	
	fun JsonReader<*>.getId(): Long {
		nextToken // "
		fillName()
		nextToken // start id
		return readLong()
	}
	
	fun FastJsonReader.getSide(): Side {
		readProperty()
		return Side.valueOf(readString())
	}
	
	fun JsonReader<*>.getSide(): Side {
		nextToken // "
		fillName()
		nextToken // start side
		return Side.valueOf(readString())
	}
	
	fun FastJsonReader.getAmount(): Double {
		readProperty() // "amount"
		return readDouble()
	}
	
	fun JsonReader<*>.getAmount(): Double {
		nextToken // "
		fillName()
		nextToken // start double
		return readDouble()
	}
	
	fun FastJsonReader.getPrice() = getAmount()
	
	fun JsonReader<*>.getPrice() = getAmount()
	
	fun HashMap<String, Book>.sendToInstruments() = forEach { entry ->
		val instrument = partition.exchange.instruments[entry.key]
		instrument?.let {
			it.processBook(entry.value)
			it.flush(System.currentTimeMillis())
			// symbolsAffected.add(it.info.symbol)
		}
	}
	
	fun FastJsonReader.getAction(): String? {
		readChar()
		return if (jumpToProperty("action")) readString() else null
	}
	
	fun JsonReader<*>.getAction(): String {
		nextToken // :
		nextToken // start action
		fillName() // action
		nextToken // start action
		return readString()
	}
	
	/*
		@Suppress("BlockingMethodInNonBlockingContext")
		fun JsonReader<*>.processBitmex() {
			startObject()
			nextToken // "
			fillName() // table
			nextToken // :
			val table = readString()
			nextToken // ,
			when (table) {
				"trade" -> processTradeTable()
			}
		}
		
		 fun JsonReader<*>.processTradeTable() {
			nextToken // start action
			fillName() // action
			nextToken // :
			when (readString()) {
				"insert" -> {
					processTradeArray()
				}
			}
		}
		
		 fun JsonReader<*>.processTradeArray() {
			nextToken // ,
			nextToken // start data
			val dataFill = readString() // data
			nextToken // :
			startArray()
			do {
				startObject()
				val trade = TradeBinder.bind(this, Trade.getNext())
			} while (nextToken == ','.toByte())
			// val trades: Array<Trade>? = readArray(dslJson.tryFindReader(Trade::class.java)!!, arr)
		}
	*/
	
	// signature is hex(HMAC_SHA256(secret, 'GET/realtime' + expires))
	// expires must be a number, not a string.
	// {"op": "authKeyExpires", "args": ["<APIKey>", <expires>, "<signature>"]}
	fun getAuthMessage(): String {
		val apiKey = BitmexAuth.apiID
		val expires = Instant.now().epochSecond + 10
		val signature = BitmexAuth.getWebsocketSignature(expires)
		return """
			{"op": "authKeyExpires", "args": ["$apiKey", $expires, "$signature"]}
		""".trimIndent()
	}
	
	fun getSubscriptionMessage(with: Boolean = false): String {
		class BitmexSubscriptionSettings {
			// public
			val orderBookL2: Boolean = true
			
			// val orderBook10: Boolean = true
			val trade: Boolean = true
			val instrument: Boolean = true
			val liquidation: Boolean = true
			
			//
			val position: Boolean = with
			val settlement: Boolean = with
			val order: Boolean = with
			
			fun getSubscriptionArray(): String {
				val arr = mutableListOf<String>()
				this::class.java.declaredFields.filter { it.type == Boolean::class.java }.forEach {
					if (it.getBoolean(this) == true) arr.add(it.name)
				}
				return arr.map { "\"$it\"" }.joinToString(",")
			}
			
		}
		
		return """{"op":"subscribe","args":[${BitmexSubscriptionSettings().getSubscriptionArray()}]}"""
	}
	
	fun SmoothieMap<String, SmoothieMap<Long, Double>>.getInnerMapOrCreate(sym: String): SmoothieMap<Long, Double> {
		val inner = get(sym)
		return if (inner != null) inner else {
			val newMap = SmoothieMap<Long, Double>()
			put(sym, newMap)
			return newMap
		}
	}
	
	class TemporaryBookState(val tempBook: Book) {
		val bidsOps = mutableListOf<BookOp>()
		val asksOps = mutableListOf<BookOp>()
		
		fun processAtInstrument(instrument: Instrument) = with(instrument) {
			instrument.processBook(tempBook)
			flush(System.currentTimeMillis())
			if (bidsOps.isNotEmpty()) {
				bidsOps.processAllAt(book.bids)
				flush(System.currentTimeMillis())
			}
			if (asksOps.isNotEmpty()) {
				asksOps.processAllAt(book.asks)
				flush(System.currentTimeMillis())
			}
		}
		
		fun MutableList<BookOp>.processAllAt(bookSide: BookSide) {
			forEach {
				when (it.type) {
					OpType.INSERT -> bookSide.processInsert(it)
					OpType.CHANGE -> bookSide.processChange(it)
					OpType.DELETE -> bookSide.processDelete(it)
				}
			}
		}
	}
}
