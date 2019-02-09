package com.nobodyhub.transcendence.api.throttle.policy.repository;

import com.nobodyhub.transcendence.api.throttle.policy.domain.ThrottlePolicy;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ThrottlePolicyRepository extends CrudRepository<ThrottlePolicy, String> {
    Optional<ThrottlePolicy> getByBucket(String bucket);
}