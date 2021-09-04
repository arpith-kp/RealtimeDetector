package org.example.zolve.flinkOperators;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;


/**
 * This class filters duplicates that occur within a configurable time of each other in a data stream.
 */
public class DedupOperator<T, K extends Serializable> extends RichFilterFunction<T>  {

	private LoadingCache<K, Boolean> dedupeCache;
	private final KeySelector<T, K> keySelector;
	private final long cacheExpirationTimeMs;

	/**
	 * @param cacheExpirationTimeMs The expiration time for elements in the cache
	 */
	public DedupOperator(KeySelector<T, K> keySelector, long cacheExpirationTimeMs){
		this.keySelector = keySelector;
		this.cacheExpirationTimeMs = cacheExpirationTimeMs;
	}

	@Override
	public void open(Configuration parameters) throws Exception {
		createDedupeCache();
	}


	@Override
	public boolean filter(T value) throws Exception {
		K key = keySelector.getKey(value);
		boolean seen = dedupeCache.get(key);
		if (!seen) {
			dedupeCache.put(key, true);
			return true;
		} else {
			return false;
		}
	}


	private void createDedupeCache() {
		dedupeCache = CacheBuilder.newBuilder()
				.expireAfterWrite(cacheExpirationTimeMs, TimeUnit.MILLISECONDS)
				.build(new CacheLoader<K, Boolean>() {
					@Override
					public Boolean load(K k) throws Exception {
						return false;
					}
				});
	}
}
