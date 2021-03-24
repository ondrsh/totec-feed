/**
 * Created by ndrsh on 6/22/20
 */

package com.totec.trading.network

import com.totec.trading.core.exchanges.Exchange
import com.totec.trading.core.partition.Partition
import com.totec.trading.network.feeds.LiveFeed
import javax.inject.Inject

class LivePartition @Inject constructor(val exchange: Exchange,
                                        override val feed: LiveFeed) : Partition {
    
    @Inject
    fun injectIntoLiveFeed() {
        feed.partition = this
    }
}
