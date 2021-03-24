/**
 * Created by ndrsh on 6/30/20
 */

package com.totec.trading.network.feeds

import com.totec.trading.core.feed.Feed
import com.totec.trading.network.LivePartition

abstract class LiveFeed() : Feed {
	override lateinit var partition: LivePartition
	lateinit var connectedCallback: () -> Unit
	
	abstract fun connect(block: () -> Unit)
	abstract fun reset()
	abstract fun disconnect()
	
	fun reconnect() {
		disconnect()
		reset()
		connect(connectedCallback)
	}
}