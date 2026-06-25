package com.turkcell.subscription_service.repository;

import com.turkcell.subscription_service.entity.SimCard;
import com.turkcell.subscription_service.enums.SimCardStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimCardRepository extends JpaRepository<SimCard, String> {

    Optional<SimCard> findFirstByStatus(SimCardStatus status);

    Optional<SimCard> findByMsisdn(String msisdn);

    boolean existsByIccid(String iccid);

    boolean existsByImsi(String imsi);
}
