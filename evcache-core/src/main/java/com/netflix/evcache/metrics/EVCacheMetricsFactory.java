package com.netflix.evcache.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
//import com.netflix.servo.DefaultMonitorRegistry;
//import com.netflix.servo.annotations.DataSourceType;
//import com.netflix.servo.monitor.BasicCounter;
//import com.netflix.servo.monitor.Counter;
//import com.netflix.servo.monitor.LongGauge;
//import com.netflix.servo.monitor.Monitor;
//import com.netflix.servo.monitor.MonitorConfig;
//import com.netflix.servo.monitor.Monitors;
//import com.netflix.servo.monitor.MonitorConfig.Builder;
//import com.netflix.servo.monitor.PercentileTimer;
//import com.netflix.servo.monitor.StepCounter;
//import com.netflix.servo.monitor.Timer;
//import com.netflix.servo.stats.StatsConfig;
//import com.netflix.servo.tag.BasicTagList;
//import com.netflix.servo.tag.Tag;
//import com.netflix.servo.tag.TagList;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Tag;
//import com.netflix.servo.tag.BasicTag;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.histogram.PercentileTimer;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = { "NF_LOCAL_FAST_PROPERTY",
        "PMB_POSSIBLE_MEMORY_BLOAT" }, justification = "Creates only when needed")
public final class EVCacheMetricsFactory {

    private final Map<String, Number> monitorMap = new ConcurrentHashMap<String, Number>();
    private final Map<String, Counter> counterMap = new ConcurrentHashMap<String, Counter>();
    private final Map<String, DistributionSummary> distributionSummaryMap = new ConcurrentHashMap<String, DistributionSummary>();
    private final Lock writeLock = (new ReentrantReadWriteLock()).writeLock();
    private final Map<String, Timer> timerMap = new HashMap<String, Timer>();

    private static final EVCacheMetricsFactory INSTANCE = new EVCacheMetricsFactory();

    private EVCacheMetricsFactory() {
        
    }
    
    public static EVCacheMetricsFactory getInstance() {
        return INSTANCE;
    }

    public Map<String, Counter> getAllCounters() {
        return counterMap;
    }
    
    public Map<String, Timer> getAllTimers() {
        return timerMap;
    }
    
    public Map<String, Number> getAllMonitor() {
        return monitorMap;
    }
    
    public Map<String, DistributionSummary> getAllDistributionSummaryMap() {
        return distributionSummaryMap;
    }

    public Registry getRegistry() {
        return Spectator.globalRegistry();
    }

    public AtomicLong getLongGauge(String name) {
        return getLongGauge(name, null);
    }

    public AtomicLong getLongGauge(String cName, Collection<Tag> tags) {
        final String name = tags != null ? cName + tags.toString() : cName;
        AtomicLong gauge = (AtomicLong)monitorMap.get(name);
        if (gauge == null) {
            writeLock.lock();
            try {
                if (monitorMap.containsKey(name)) {
                    gauge = (AtomicLong)monitorMap.get(name);
                } else {
                    if(tags != null) {
                        gauge = getRegistry().gauge(cName, tags, new AtomicLong());
                    } else {
                        gauge = getRegistry().gauge(cName, new AtomicLong());
                    }
                    monitorMap.put(name, gauge);
                }
            } finally {
                writeLock.unlock();
            }
        }
        return gauge;
    }
    
    public Id getId(String name, Collection<Tag> tags) {
        List<Tag> tagList = new ArrayList<Tag>(1);
        tagList.addAll(tags);
        tagList.add(new BasicTag("owner", "evcache"));
        return getRegistry().createId(name, tagList);
    }

    public Counter getCounter(String cName, Collection<Tag> tags) {
        final String name = tags != null ? cName + tags.toString() : cName;
        Counter counter = counterMap.get(name);
        if (counter == null) {
            writeLock.lock();
            try {
                if (counterMap.containsKey(name)) {
                    counter = counterMap.get(name);
                } else {
                    List<Tag> tagList = new ArrayList<Tag>(1);
                    tagList.addAll(tags);
                    tagList.add(new BasicTag("owner", "evcache"));
                    counter = getRegistry().counter(cName, tagList);
                    counterMap.put(name, counter);
                }
            } finally {
                writeLock.unlock();
            }
        }
        return counter;
    }


    public Counter getCounter(String name) {
        return getCounter(name, null);
    }

    public void increment(String name) {
        final Counter counter = getCounter(name);
        counter.increment();
    }

    public void increment(String cName, Collection<Tag> tags) {
        final Counter counter = getCounter(cName, tags);
        counter.increment();
    }

    public Timer getPercentileTimer(String metric, Collection<Tag> tags) {
        final String name = tags != null ? metric + tags.toString() : metric;
        final Timer duration = timerMap.get(name);
        if (duration != null) return duration;

        writeLock.lock();
        try {
            if (timerMap.containsKey(name))
                return timerMap.get(name);
            else {
                Id id = getId(metric, tags);
                final Timer _duration = PercentileTimer.get(getRegistry(), id);
                timerMap.put(name, _duration);
                return _duration;
            }
        } finally {
            writeLock.unlock();
        }
    }

    public DistributionSummary getDistributionSummary(String name, Collection<Tag> tags) {
        final String metricName = (tags != null ) ? name + tags.toString() : name;
        final DistributionSummary _ds = distributionSummaryMap.get(metricName);
        if(_ds != null) return _ds;
        final Registry registry = Spectator.globalRegistry(); 
        if (registry != null) {
            Id id = getId(name, tags);
            final DistributionSummary ds = registry.distributionSummary(id);
            distributionSummaryMap.put(metricName, ds);
            return ds;
        }
        return null;
    }


    /**
     * External Metric Names
     */
    public static final String CALL                             = "evcache.client.call";
    public static final String FAST_FAIL                        = "evcache.client.fastfail";
    public static final String CONFIG                           = "evcache.client.config";
    public static final String DATA_SIZE                        = "evcache.client.datasize";
    public static final String IN_MEMORY                        = "evcache.client.inmemorycache";

    /**
     * Internal Metric Names
     */
    public static final String INTERNAL_CALL                    = "internal.evcache.client.call";
    public static final String INTERNAL_CONFIG                  = "internal.evcache.client.config";
    public static final String INTERNAL_PAUSE                   = "internal.evcache.client.pause";
    public static final String INTERNAL_LATCH                   = "internal.evcache.client.latch";

    public static final String INTERNAL_NUM_CHUNK_SIZE          = "internal.evcache.client.chunking.numOfChunks";
    public static final String INTERNAL_CHUNK_DATA_SIZE         = "internal.evcache.client.chunking.dataSize";

    public static final String INTERNAL_POOL_REFRESH            = "internal.evcache.client.pool.refresh";
    public static final String INTERNAL_POOL_SIZE               = "internal.evcache.client.pool.size";
    public static final String INTERNAL_POOL_ACTIVE             = "internal.evcache.client.pool.activeConnections";
    public static final String INTERNAL_POOL_INACTIVE           = "internal.evcache.client.pool.inActiveConnections";
    public static final String INTERNAL_POOL_IN_DISCOVERY       = "internal.evcache.client.pool.inDiscovery";
    public static final String INTERNAL_POOL_IN_HASHING         = "internal.evcache.client.pool.inHashing";
    public static final String INTERNAL_POOL_READ_INSTANCES     = "internal.evcache.client.pool.readInstances";
    public static final String INTERNAL_POOL_WRITE_INSTANCES    = "internal.evcache.client.pool.writeInstances";
    public static final String INTERNAL_POOL_RECONCILE          = "internal.evcache.client.pool.reconcile";
    public static final String INTERNAL_POOL_CHANGED            = "internal.evcache.client.pool.serverGroupChanged";
    public static final String INTERNAL_POOL_SERVERGROUP_STATUS = "internal.evcache.client.pool.serverGroup.status";
    public static final String INTERNAL_POOL_INIT_ERROR         = "internal.evcache.client.pool.init.error";
    public static final String INTERNAL_POOL_READ_Q_SIZE        = "internal.evcache.client.pool.readQueue";
    public static final String INTERNAL_POOL_WRITE_Q_SIZE       = "internal.evcache.client.pool.writeQueue";
    public static final String INTERNAL_POOL_REFRESH_QUEUE_FULL = "internal.evcache.client.pool.refresh.on.queue.full";
    public static final String INTERNAL_POOL_REFRESH_ASYNC      = "internal.evcache.client.pool.refresh.async";
    public static final String INTERNAL_ADD_CALL_FIXUP          = "internal.evcache.client.addCall.fixUp";

    /**
     * Metric Tags Names
     */
    public static final String CACHE                            = "cache";
    public static final String CAUSE                            = "cause";
    public static final String SERVERGROUP                      = "serverGroup";
    public static final String FAIL_COUNT                       = "failCount";
    public static final String COMPLETE_COUNT                   = "completeCount";
    public static final String RECONNECT_COUNT                  = "reconnectCount";
    public static final String STATUS                           = "status";
    public static final String PAUSE_REASON                     = "pauseReason";
    public static final String FETCH_AFTER_PAUSE                = "fetchAfterPause";
    public static final String FAILED_SERVERGROUP               = "failedServerGroup";
    public static final String CONFIG_NAME                      = "configName";
    public static final String HOST                             = "host";
    public static final String CACHE_HIT                        = "cacheHit";
    public static final String OPERATION                        = "operation";
    public static final String OPERATION_TYPE                   = "operationType";
    public static final String OPERATION_STATUS                 = "operationStatus";
    public static final String NUMBER_OF_ATTEMPTS               = "numberOfAttempts";
    public static final String NUMBER_OF_KEYS                   = "numberOfKeys";
    public static final String METRIC                           = "metric";
    public static final String PREFIX                           = "prefix";

    /**
     * Metric Tags Values  
     */
    public static final String SIZE                             = "size";
    public static final String PORT                             = "port";
    public static final String CONNECT                          = "connect";
    public static final String DISCONNECT                       = "disconnect";
    public static final String SUCCESS                          = "success";
    public static final String TIMEOUT                          = "timeout";
    public static final String CANCELLED                        = "cancelled";
    public static final String THROTTLED                        = "throttled";
    public static final String ERROR                            = "error";
    public static final String READ                             = "read";
    public static final String WRITE                            = "write";
    public static final String YES                              = "yes";
    public static final String NO                               = "no";
    public static final String PARTIAL                          = "partial";
    public static final String UNKNOWN                          = "unknown";
    public static final String GC                               = "gc";
    public static final String NULL_CLIENT                      = "nullClient";
    public static final String NULL_ZONE                        = "nullZone";
    public static final String NULL_SERVERGROUP                 = "nullServerGroup";
    public static final String RECONNECT                        = "reconnect";
    public static final String CALLBACK                         = "callback";
    public static final String VERIFY                           = "verify";
    public static final String READ_QUEUE_FULL                  = "readQueueFull";
    public static final String INACTIVE_NODE                    = "inactiveNode";
    public static final String INCORRECT_CHUNKS                 = "incorrectNumOfChunks";
    public static final String INVALID_CHUNK_SIZE               = "invalidChunkSize";
    public static final String CHECK_SUM_ERROR                  = "checkSumError";
    public static final String NUM_CHUNK_SIZE                   = "numOfChunks";
    public static final String CHUNK_DATA_SIZE                  = "dataSize";

    /**
     * Metric Tag Value for Operations
     */
    public static final String BULK_OPERATION                   = "BULK";
    public static final String GET_OPERATION                    = "GET";
    public static final String GET_AND_TOUCH_OPERATION          = "GET_AND_TOUCH";
    public static final String DELETE_OPERATION                 = "DELETE";
    public static final String TOUCH_OPERATION                  = "TOUCH";
    public static final String AOA_OPERATION                    = "APPEND_OR_ADD";
    public static final String SET_OPERATION                    = "SET";
    public static final String ADD_OPERATION                    = "ADD";
    public static final String REPLACE_OPERATION                = "REPLACE";


}