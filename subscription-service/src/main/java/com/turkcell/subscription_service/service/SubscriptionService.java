package com.turkcell.subscription_service.service;

import com.turkcell.subscription_service.dto.request.ActivateSubscriptionRequest;
import com.turkcell.subscription_service.dto.request.CreateMsisdnPoolRequest;
import com.turkcell.subscription_service.dto.request.CreateSimCardRequest;
import com.turkcell.subscription_service.dto.request.UpdateMnpStatusRequest;
import com.turkcell.subscription_service.dto.response.MsisdnPoolResponse;
import com.turkcell.subscription_service.dto.response.SimCardResponse;
import com.turkcell.subscription_service.dto.response.SubscriptionResponse;
import com.turkcell.subscription_service.entity.MsisdnPool;
import com.turkcell.subscription_service.entity.SimCard;
import com.turkcell.subscription_service.entity.Subscription;
import com.turkcell.subscription_service.enums.MnpStatus;
import com.turkcell.subscription_service.enums.MsisdnStatus;
import com.turkcell.subscription_service.enums.SimCardStatus;
import com.turkcell.subscription_service.enums.SubscriptionStatus;
import com.turkcell.subscription_service.exception.SubscriptionNotFoundException;
import com.turkcell.subscription_service.repository.MsisdnPoolRepository;
import com.turkcell.subscription_service.repository.SimCardRepository;
import com.turkcell.subscription_service.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    // FR-16: MNP için tam state machine engine yerine sabit, doğrulanan geçiş tablosu.
    private static final Map<MnpStatus, Set<MnpStatus>> VALID_MNP_TRANSITIONS = Map.of(
            MnpStatus.NOT_REQUESTED, Set.of(MnpStatus.REQUESTED),
            MnpStatus.REQUESTED, Set.of(MnpStatus.VALIDATION_PENDING, MnpStatus.CANCELLED),
            MnpStatus.VALIDATION_PENDING, Set.of(MnpStatus.APPROVED, MnpStatus.REJECTED, MnpStatus.CANCELLED),
            MnpStatus.APPROVED, Set.of(MnpStatus.COMPLETED));

    private final SubscriptionRepository subscriptionRepository;
    private final MsisdnPoolRepository msisdnPoolRepository;
    private final SimCardRepository simCardRepository;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            MsisdnPoolRepository msisdnPoolRepository,
            SimCardRepository simCardRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.msisdnPoolRepository = msisdnPoolRepository;
        this.simCardRepository = simCardRepository;
    }

    @Transactional
    public MsisdnPoolResponse createMsisdn(CreateMsisdnPoolRequest request) {
        if (msisdnPoolRepository.existsByMsisdn(request.msisdn())) {
            throw new IllegalArgumentException("MSISDN " + request.msisdn() + " already exists.");
        }

        MsisdnPool msisdnPool = new MsisdnPool();
        msisdnPool.setMsisdn(request.msisdn());
        msisdnPool.setStatus(MsisdnStatus.FREE);

        MsisdnPool savedMsisdnPool = msisdnPoolRepository.save(msisdnPool);

        return toMsisdnPoolResponse(savedMsisdnPool);
    }

    @Transactional
    public SimCardResponse createSimCard(CreateSimCardRequest request) {
        if (simCardRepository.existsByIccid(request.iccid())) {
            throw new IllegalArgumentException("ICCID " + request.iccid() + " already exists.");
        }

        if (simCardRepository.existsByImsi(request.imsi())) {
            throw new IllegalArgumentException("IMSI " + request.imsi() + " already exists.");
        }

        SimCard simCard = new SimCard();
        simCard.setIccid(request.iccid());
        simCard.setImsi(request.imsi());
        simCard.setMsisdn(request.msisdn());
        simCard.setStatus(SimCardStatus.FREE);

        SimCard savedSimCard = simCardRepository.save(simCard);

        return toSimCardResponse(savedSimCard);
    }

    @Transactional
    public SubscriptionResponse activate(ActivateSubscriptionRequest request) {
        if (subscriptionRepository.findByOrderId(request.orderId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Subscription for order " + request.orderId() + " already exists.");
        }

        MsisdnPool msisdnPool = msisdnPoolRepository.findFirstByStatus(MsisdnStatus.FREE)
                .orElseThrow(() -> new IllegalArgumentException("No free MSISDN available."));

        msisdnPool.setStatus(MsisdnStatus.ALLOCATED);
        msisdnPoolRepository.save(msisdnPool);

        simCardRepository.findByMsisdn(msisdnPool.getMsisdn())
                .filter(simCard -> simCard.getStatus() == SimCardStatus.FREE)
                .ifPresent(simCard -> {
                    simCard.setStatus(SimCardStatus.ACTIVE);
                    simCardRepository.save(simCard);
                });

        Subscription subscription = new Subscription();
        subscription.setOrderId(request.orderId());
        subscription.setCustomerId(request.customerId());
        subscription.setMsisdn(msisdnPool.getMsisdn());
        subscription.setTariffCode(request.tariffCode());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setMnpStatus(MnpStatus.NOT_REQUESTED);
        subscription.setActivatedAt(LocalDateTime.now());

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        return toSubscriptionResponse(savedSubscription);
    }

    public SubscriptionResponse getById(UUID id) {
        return toSubscriptionResponse(findSubscriptionById(id));
    }

    public List<SubscriptionResponse> getByCustomerId(UUID customerId) {
        return subscriptionRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    public List<SubscriptionResponse> getActiveSubscriptions() {
        return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)
                .stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    @Transactional
    public SubscriptionResponse suspend(UUID id) {
        Subscription subscription = findSubscriptionById(id);

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active subscription can be suspended.");
        }

        subscription.setStatus(SubscriptionStatus.SUSPENDED);

        return toSubscriptionResponse(subscriptionRepository.save(subscription));
    }

    @Transactional
    public SubscriptionResponse reactivate(UUID id) {
        Subscription subscription = findSubscriptionById(id);

        if (subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
            throw new IllegalArgumentException("Only a suspended subscription can be reactivated.");
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);

        return toSubscriptionResponse(subscriptionRepository.save(subscription));
    }

    @Transactional
    public SubscriptionResponse terminate(UUID id) {
        Subscription subscription = findSubscriptionById(id);

        if (subscription.getStatus() == SubscriptionStatus.TERMINATED) {
            throw new IllegalArgumentException("Subscription is already terminated.");
        }

        subscription.setStatus(SubscriptionStatus.TERMINATED);
        subscription.setTerminatedAt(LocalDateTime.now());

        return toSubscriptionResponse(subscriptionRepository.save(subscription));
    }

    @Transactional
    public SubscriptionResponse updateMnpStatus(UUID id, UpdateMnpStatusRequest request) {
        Subscription subscription = findSubscriptionById(id);

        if (subscription.getStatus() == SubscriptionStatus.TERMINATED) {
            throw new IllegalArgumentException("MNP status cannot be updated for a terminated subscription.");
        }

        Set<MnpStatus> allowedNextStatuses = VALID_MNP_TRANSITIONS.getOrDefault(
                subscription.getMnpStatus(), Set.of());

        if (!allowedNextStatuses.contains(request.mnpStatus())) {
            throw new IllegalArgumentException(
                    "Invalid MNP status transition: " + subscription.getMnpStatus() + " -> " + request.mnpStatus());
        }

        subscription.setMnpStatus(request.mnpStatus());

        return toSubscriptionResponse(subscriptionRepository.save(subscription));
    }

    private Subscription findSubscriptionById(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException("Subscription with id " + id + " not found."));
    }

    private SubscriptionResponse toSubscriptionResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getOrderId(),
                subscription.getCustomerId(),
                subscription.getMsisdn(),
                subscription.getTariffCode(),
                subscription.getStatus(),
                subscription.getMnpStatus(),
                subscription.getActivatedAt(),
                subscription.getTerminatedAt());
    }

    private MsisdnPoolResponse toMsisdnPoolResponse(MsisdnPool msisdnPool) {
        return new MsisdnPoolResponse(
                msisdnPool.getMsisdn(),
                msisdnPool.getStatus(),
                msisdnPool.getReservedUntil());
    }

    private SimCardResponse toSimCardResponse(SimCard simCard) {
        return new SimCardResponse(
                simCard.getIccid(),
                simCard.getImsi(),
                simCard.getMsisdn(),
                simCard.getStatus());
    }
}
