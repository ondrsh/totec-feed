/**
 * Created by ndrsh on 6/25/20
 */

package com.totec.trading.network

import com.totec.trading.core.partition.Partition
import dagger.Module
import dagger.Provides

@Module
class LivePartitionModule() {
	@Provides fun providePartition(livePartition: LivePartition): Partition = livePartition
}
