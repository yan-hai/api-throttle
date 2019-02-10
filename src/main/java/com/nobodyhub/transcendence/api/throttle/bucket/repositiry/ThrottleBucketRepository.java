package com.nobodyhub.transcendence.api.throttle.bucket.repositiry;

import com.google.common.collect.Lists;
import com.nobodyhub.transcendence.api.throttle.bucket.domain.BucketStatus;
import com.nobodyhub.transcendence.api.throttle.bucket.utils.BucketStatusBuilder;
import com.nobodyhub.transcendence.api.throttle.policy.domain.ThrottlePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.nobodyhub.transcendence.api.throttle.bucket.utils.ThrottleBucketNamingUtil.*;
import static com.nobodyhub.transcendence.api.throttle.policy.utils.ThrottlePolicyUtil.check;
import static com.nobodyhub.transcendence.api.throttle.policy.utils.ThrottlePolicyUtil.getWindowUpperLimit;

@Repository
@RequiredArgsConstructor
public class ThrottleBucketRepository {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Create new bucket for the given policy
     *
     * @param policy
     */
    public void createBucket(@NonNull ThrottlePolicy policy) {
        SessionCallback<List<Object>> callback = new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings({"unchecked"})
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                // watch
                operations.watch(Lists.newArrayList(
                        status(policy.getBucket()),
                        window(policy.getBucket())
                ));
                // multi
                operations.multi();
                initBucketStatus(operations, policy);
                // exec
                return operations.exec();
            }
        };
        //TODO: consider time limits on retry
        List<Object> rst = null;
        while (rst == null || rst.isEmpty()) {
            rst = this.redisTemplate.execute(callback);
        }
    }

    /**
     * Initialize the bucket status based on the policy
     *
     * @param redisOperations
     * @param policy
     * @return
     */
    @SuppressWarnings({"unchecked"})
    private BucketStatus initBucketStatus(@NonNull RedisOperations redisOperations,
                                          @NonNull ThrottlePolicy policy) {
        BucketStatus status = BucketStatusBuilder.of(policy).build();
        redisOperations.boundZSetOps(window(policy.getBucket())).add(0, 0);
        redisOperations.boundValueOps(status(policy.getBucket())).set(status);
        return status;
    }

    /**
     * Fetch the bucket status and check whether to proceed to execute
     * A new key for the execution will be added to redis and set to true if OK to execute
     *
     * @param policy
     * @return the execution token
     */
    public String updateBucket(@NonNull ThrottlePolicy policy) {
        SessionCallback<List<Object>> callback = new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings({"unchecked"})
            public List<Object> execute(RedisOperations redisOperations) throws DataAccessException {

                // watch
                redisOperations.watch(Lists.newArrayList(
                        status(policy.getBucket()),
                        window(policy.getBucket())
                ));
                long timestamp = getServerTime();
                String execToken = UUID.randomUUID().toString();
                BucketStatus status = getBucketStatus(redisOperations, timestamp, policy);
                // multi
                List<Object> ret = Lists.newArrayList();
                redisOperations.multi();
                if (check(policy, timestamp, status)) {
                    // update the status
                    status = BucketStatusBuilder.of(status)
                            .decreaseNToken()
                            .lastRequest(timestamp)
                            .build();
                    updateBucketStatus(redisOperations, status, execToken);
                    updateExecToken(redisOperations, policy.getBucket(), execToken);
                    // exec
                    ret = redisOperations.exec();
                } else {
                    // discard
                    redisOperations.discard();
                }
                ret.add(execToken);
                return ret;
            }
        };

        //TODO: consider time limits on retry
        List<Object> rst = null;
        while (rst == null || rst.size() <= 1) {
            rst = this.redisTemplate.execute(callback);
        }
        return (String) rst.get(rst.size() - 1);
    }

    /**
     * Get bucket status
     *
     * @param redisOperations
     * @param timestamp
     * @param policy
     * @return
     */
    @SuppressWarnings({"unchecked"})
    private BucketStatus getBucketStatus(@NonNull RedisOperations redisOperations,
                                         @NonNull long timestamp,
                                         @NonNull ThrottlePolicy policy) {
        // bucket tokens
        BucketStatus status = (BucketStatus) redisOperations.boundValueOps(status(policy.getBucket())).get();
        if (status == null) {
            status = initBucketStatus(redisOperations, policy);
        }
        // bucket windowed history
        redisOperations.boundZSetOps(window(policy.getBucket())).removeRangeByScore(0, getWindowUpperLimit(policy, timestamp));
        Long nWindowed = redisOperations.boundZSetOps(window(policy.getBucket())).size();
        if (nWindowed != null) {
            status.setNWindowed(nWindowed);
        }
        return status;
    }

    /**
     * Get status for given bucket
     *
     * @param bucket
     * @return
     */
    @Nullable
    public BucketStatus getBucketStatus(@NonNull String bucket) {
        BucketStatus status = (BucketStatus) redisTemplate.boundValueOps(status(bucket)).get();
        Long nWindowed = redisTemplate.boundZSetOps(window(bucket)).size();
        if (status != null && nWindowed != null) {
            status.setNWindowed(nWindowed);
        }
        return status;
    }

    /**
     * Get bucket status for given bucket names
     *
     * @param buckets bucket name list
     * @return bucket status list
     */
    @NotNull
    public List<BucketStatus> getBucketStatus(@NotNull String... buckets) {
        SessionCallback<List<Object>> callback = new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                for (String bucket : buckets) {
                    redisTemplate.boundValueOps(status(bucket)).get();
                    redisTemplate.boundZSetOps(window(bucket)).size();
                }
                return null;
            }
        };
        List<Object> retVals = this.redisTemplate.executePipelined(callback);
        List<BucketStatus> results = Lists.newArrayList();
        for (int idx = 0; idx < retVals.size(); idx = idx + 2) {
            if (idx + 1 < retVals.size()) {
                BucketStatus status = (BucketStatus) retVals.get(idx);
                Long nWindowed = (Long) retVals.get(idx + 1);
                if (status != null) {
                    if (nWindowed != null) {
                        status.setNWindowed(nWindowed);
                    }
                    results.add(status);
                }
            }
        }
        return results;
    }

    /**
     * Check the value of execution token
     *
     * @param bucket
     * @param execToken
     * @return true if OK to execute
     */
    public boolean checkExecToken(@NonNull String bucket,
                                  @NonNull String execToken) {
        String key = execution(bucket, execToken);
        boolean rst = Boolean.TRUE.equals(this.redisTemplate.boundValueOps(key).get());
        redisTemplate.delete(key);
        return rst;
    }


    /**
     * Update the bucket status
     *
     * @param redisOperations
     * @param status
     * @param execToken
     */
    @SuppressWarnings({"unchecked"})
    private void updateBucketStatus(@NonNull RedisOperations redisOperations,
                                    @NonNull BucketStatus status,
                                    @NonNull String execToken) {
        redisOperations.boundValueOps(status(status.getBucket())).set(status);
        redisOperations.boundZSetOps(window(status.getBucket())).add(execToken, status.getLastRequest());
    }

    /**
     * Put the execution token to Redis
     *
     * @param redisOperations
     * @param bucket
     * @param execToken
     */
    @SuppressWarnings({"unchecked"})
    private void updateExecToken(@NonNull RedisOperations redisOperations,
                                 @NonNull String bucket,
                                 @NonNull String execToken) {
        String key = execution(bucket, execToken);
        redisOperations.boundValueOps(key).set(String.valueOf(true));
        // TODO: make the expire time configurable
        redisOperations.expire(key, 1, TimeUnit.MINUTES);
    }


    /**
     * Try to current time of redis server, if fails, use client time
     *
     * @return time in milliseconds
     * @see RedisServerCommands#time()
     */
    private long getServerTime() {
        Long timestamp = RedisConnectionUtils.getConnection(redisTemplate.getRequiredConnectionFactory()).time();
        return timestamp == null ? System.currentTimeMillis() : timestamp;
    }
}
