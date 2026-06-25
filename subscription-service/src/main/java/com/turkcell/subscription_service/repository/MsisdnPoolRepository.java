package com.turkcell.subscription_service.repository;

import com.turkcell.subscription_service.entity.MsisdnPool;
import com.turkcell.subscription_service.enums.MsisdnStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MsisdnPoolRepository extends JpaRepository<MsisdnPool, String> {

    Optional<MsisdnPool> findFirstByStatus(MsisdnStatus status);

    Optional<MsisdnPool> findByMsisdn(String msisdn);

    boolean existsByMsisdn(String msisdn);
}
