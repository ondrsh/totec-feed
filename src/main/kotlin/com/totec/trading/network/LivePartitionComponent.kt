/**
 * Created by ndrsh on 13.06.20
 */

package com.totec.trading.network

import com.totec.trading.core.exchanges.ExchangeComponent
import com.totec.trading.core.partition.PartitionComponent
import com.totec.trading.core.partition.PartitionScope
import com.totec.trading.network.feeds.LiveFeed
import dagger.BindsInstance
import dagger.Component

@Component(modules = [LivePartitionModule::class], dependencies = [ExchangeComponent::class])
@PartitionScope
interface LivePartitionComponent : PartitionComponent {
	
	override fun partition(): LivePartition
	fun exchangeComponent(): ExchangeComponent
	
	@Component.Factory
	interface Factory {
		fun create(exchangeComponent: ExchangeComponent,
		           @BindsInstance liveFeed: LiveFeed): LivePartitionComponent
	}
}
