package com.nobodyhub.transcendence.api.throttle.bucket.utils;

import com.nobodyhub.transcendence.api.throttle.bucket.domain.BucketStatus;
import com.nobodyhub.transcendence.api.throttle.policy.domain.ThrottlePolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * Builder/Updater for {@link BucketStatus}
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BucketStatusBuilder {
    private BucketStatus status;

    /**
     * create from bucket name
     *
     * @param bucket
     * @return
     */
    public static BucketStatusBuilder of(String bucket) {
        BucketStatus status = new BucketStatus();
        status.setBucket(bucket);
        return new BucketStatusBuilder(status);
    }

    /**
     * Update given status
     *
     * @param status
     * @return
     */
    public static BucketStatusBuilder of(BucketStatus status) {
        return new BucketStatusBuilder(status);
    }

    /**
     * Create from policy
     *
     * @param policy
     * @return
     */
    public static BucketStatusBuilder of(ThrottlePolicy policy) {
        BucketStatusBuilder builder = BucketStatusBuilder.of(policy.getBucket());
        if (policy.getNToken() != null) {
            builder = builder.nToken(policy.getNToken());
        }
        return builder;
    }

    public BucketStatusBuilder nToken(long nToken) {
        this.status.setNToken(nToken);
        return this;
    }

    public BucketStatusBuilder decreaseNToken() {
        this.status.setNToken(this.status.getNToken() - 1);
        return this;
    }

    public BucketStatusBuilder lastRequest(long lastRequest) {
        this.status.setLastRequest(lastRequest);
        return this;
    }

    public BucketStatusBuilder nWindowed(long nWindowed) {
        this.status.setNToken(nWindowed);
        return this;
    }

    public BucketStatus build() {
        return this.status;
    }
}
