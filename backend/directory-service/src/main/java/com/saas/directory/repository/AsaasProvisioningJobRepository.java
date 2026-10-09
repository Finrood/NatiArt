package com.saas.directory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.saas.directory.model.AsaasProvisioningJob;
import com.saas.directory.model.AsaasProvisioningStatus;
import com.saas.directory.model.User;
import com.saas.directory.model.helper.PaymentProcessor;

@Repository
public interface AsaasProvisioningJobRepository extends JpaRepository<AsaasProvisioningJob, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AsaasProvisioningJob> findByUserAndPaymentProcessor(User user, PaymentProcessor paymentProcessor);

    Optional<AsaasProvisioningJob> findByUserIdAndPaymentProcessor(String userId, PaymentProcessor paymentProcessor);

    Optional<AsaasProvisioningJob> findByGuestCustomerIdAndPaymentProcessor(
            String guestCustomerId, PaymentProcessor processor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AsaasProvisioningJob j where j.id = :id")
    Optional<AsaasProvisioningJob> findByIdForUpdate(@Param("id") String id);

    List<AsaasProvisioningJob> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            List<AsaasProvisioningStatus> statuses, Instant now);
}
