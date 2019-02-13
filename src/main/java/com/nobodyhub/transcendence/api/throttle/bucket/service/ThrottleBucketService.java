package com.nobodyhub.transcendence.api.throttle.bucket.service;

import com.google.common.collect.Lists;
import com.nobodyhub.transcendence.api.throttle.bucket.domain.BucketStatus;
import com.nobodyhub.transcendence.api.throttle.bucket.repositiry.ThrottleBucketRepository;
import com.nobodyhub.transcendence.api.throttle.policy.domain.ThrottlePolicy;
import com.nobodyhub.transcendence.api.throttle.policy.service.ThrottlePolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service that controls the bucket status
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThrottleBucketService {
    private final ThrottleBucketRepository bucketRepository;
    private final ThrottlePolicyService policyService;

    @Nullable
    public List<BucketStatus> findBucket(@NonNull List<String> buckets) {
        return bucketRepository.getBucketStatus(buckets);
    }

    /**
     * Get bucket status by name
     *
     * @param bucket bucket status
     * @return
     */
    @Nullable
    public BucketStatus findBucket(@NonNull String bucket) {
        return bucketRepository.getBucketStatus(bucket);
    }

    /**
     * Create new bucket for the policy of given name
     *
     * @param bucket
     */
    public void createBucket(@NonNull String bucket) {
        ThrottlePolicy policy = policyService.find(bucket);
        if (policy != null) {
            bucketRepository.createBucket(policy);
            return;
        }
        log.warn("No policy found for Bucket: {}! No throttle will be applied!", bucket);
    }

    /**
     * Update bucket status
     *
     * @param buckets
     * @return true to proceed execution
     */
    public boolean checkBucket(@NonNull String... buckets) {
        List<ThrottlePolicy> policies = Lists.newArrayList();
        for (String bucket : buckets) {
            ThrottlePolicy policy = policyService.find(bucket);
            if (policy != null) {
                policies.add(policy);
            }
        }
        if (!policies.isEmpty() && policies.size() == buckets.length) {
            return bucketRepository.checkBucket(policies);
        }
        return false;
    }
}
