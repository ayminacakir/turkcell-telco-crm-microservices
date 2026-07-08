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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.subscription_service.kafka.entity.ProcessedEvent;
import com.turkcell.subscription_service.kafka.event.PaymentCompletedEvent;
import com.turkcell.subscription_service.kafka.event.PaymentFailedEvent;
import com.turkcell.subscription_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.subscription_service.outbox.entity.OutboxEvent;
import com.turkcell.subscription_service.outbox.event.SubscriptionActivatedEvent;
import com.turkcell.subscription_service.outbox.event.SubscriptionSuspendedEvent;
import com.turkcell.subscription_service.outbox.event.SubscriptionTerminatedEvent;
import com.turkcell.subscription_service.outbox.enums.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.turkcell.subscription_service.outbox.repository.OutboxEventRepository;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionService.class);

    // FR-16: MNP için tam state machine engine yerine sabit, doğrulanan geçiş tablosu.
    private static final Map<MnpStatus, Set<MnpStatus>> VALID_MNP_TRANSITIONS = Map.of(
            MnpStatus.NOT_REQUESTED, Set.of(MnpStatus.REQUESTED),
            MnpStatus.REQUESTED, Set.of(MnpStatus.VALIDATION_PENDING, MnpStatus.CANCELLED),
            MnpStatus.VALIDATION_PENDING, Set.of(MnpStatus.APPROVED, MnpStatus.REJECTED, MnpStatus.CANCELLED),
            MnpStatus.APPROVED, Set.of(MnpStatus.COMPLETED));

    private final SubscriptionRepository subscriptionRepository;
    private final MsisdnPoolRepository msisdnPoolRepository;
    private final SimCardRepository simCardRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            MsisdnPoolRepository msisdnPoolRepository,
            SimCardRepository simCardRepository,
            OutboxEventRepository outboxEventRepository,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.msisdnPoolRepository = msisdnPoolRepository;
        this.simCardRepository = simCardRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
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
        saveSubscriptionActivatedOutboxEvent(savedSubscription);

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

        Subscription savedSuspended = subscriptionRepository.save(subscription);
        saveSubscriptionSuspendedOutboxEvent(savedSuspended);
        return toSubscriptionResponse(savedSuspended);
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

        Subscription savedTerminated = subscriptionRepository.save(subscription);
        saveSubscriptionTerminatedOutboxEvent(savedTerminated);
        return toSubscriptionResponse(savedTerminated);
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

    private void saveSubscriptionActivatedOutboxEvent(Subscription subscription) {
        SubscriptionActivatedEvent eventPayload = new SubscriptionActivatedEvent(
                UUID.randomUUID(),
                "SubscriptionActivated",
                subscription.getOrderId(),
                subscription.getId(),
                subscription.getCustomerId(),
                subscription.getTariffCode(),
                subscription.getMsisdn(),
                null,
                null,
                null,
                subscription.getActivatedAt());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SubscriptionActivated event", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(subscription.getId());
        outboxEvent.setAggregateType("SUBSCRIPTION");
        outboxEvent.setEventType("SubscriptionActivated");
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
    }

    private void saveSubscriptionSuspendedOutboxEvent(Subscription subscription) {
        SubscriptionSuspendedEvent event = new SubscriptionSuspendedEvent(
                UUID.randomUUID(),
                "SubscriptionSuspended",
                subscription.getId(),
                subscription.getCustomerId(),
                subscription.getMsisdn(),
                subscription.getTariffCode(),
                LocalDateTime.now());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SubscriptionSuspended event", e);
        }
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(subscription.getId());
        outboxEvent.setAggregateType("SUBSCRIPTION");
        outboxEvent.setEventType("SubscriptionSuspended");
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
    }

    private void saveSubscriptionTerminatedOutboxEvent(Subscription subscription) {
        SubscriptionTerminatedEvent event = new SubscriptionTerminatedEvent(
                UUID.randomUUID(),
                "SubscriptionTerminated",
                subscription.getId(),
                subscription.getCustomerId(),
                subscription.getMsisdn(),
                subscription.getTariffCode(),
                LocalDateTime.now());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SubscriptionTerminated event", e);
        }
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(subscription.getId());
        outboxEvent.setAggregateType("SUBSCRIPTION");
        outboxEvent.setEventType("SubscriptionTerminated");
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        LOGGER.info("Processing PaymentFailed event eventId={} orderId={}", event.eventId(), event.orderId());

        if (processedEventRepository.existsById(event.eventId())) {
            LOGGER.info("PaymentFailed event already processed eventId={}", event.eventId());
            return;
        }

        subscriptionRepository.findByOrderId(event.orderId()).ifPresent(subscription -> {
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                subscription.setStatus(SubscriptionStatus.SUSPENDED);
                Subscription saved = subscriptionRepository.save(subscription);
                saveSubscriptionSuspendedOutboxEvent(saved);
                LOGGER.info("Suspended subscription {} due to payment failure orderId={}",
                        saved.getId(), event.orderId());
            }
        });

        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEventRepository.save(processedEvent);
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        LOGGER.info("Processing PaymentCompleted event eventId={} orderId={}", event.eventId(), event.orderId());

        if (processedEventRepository.existsById(event.eventId())) {
            LOGGER.info("PaymentCompleted event already processed eventId={}", event.eventId());
            return;
        }

        if (subscriptionRepository.findByOrderId(event.orderId()).isPresent()) {
            LOGGER.info("Subscription already exists for orderId={}, marking event processed", event.orderId());
            saveProcessedEvent(event);
            return;
        }

        var optionalMsisdn = msisdnPoolRepository.findFirstByStatus(MsisdnStatus.FREE);
        if (optionalMsisdn.isEmpty()) {
            LOGGER.warn("No free MSISDN available for PaymentCompleted event eventId={}", event.eventId());
            return;
        }

        MsisdnPool msisdnPool = optionalMsisdn.get();
        var optionalSimCard = simCardRepository.findByMsisdn(msisdnPool.getMsisdn())
                .filter(simCard -> simCard.getStatus() == SimCardStatus.FREE);

        if (optionalSimCard.isEmpty()) {
            LOGGER.warn("No free SIM card available for MSISDN {} in PaymentCompleted event eventId={}",
                    msisdnPool.getMsisdn(), event.eventId());
            return;
        }

        SimCard simCard = optionalSimCard.get();

        msisdnPool.setStatus(MsisdnStatus.ALLOCATED);
        msisdnPoolRepository.save(msisdnPool);

        simCard.setStatus(SimCardStatus.ACTIVE);
        simCard.setMsisdn(msisdnPool.getMsisdn());
        simCardRepository.save(simCard);

        Subscription subscription = new Subscription();
        subscription.setOrderId(event.orderId());
        subscription.setCustomerId(event.customerId());
        subscription.setMsisdn(msisdnPool.getMsisdn());
        subscription.setTariffCode(event.tariffCode());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setMnpStatus(MnpStatus.NOT_REQUESTED);
        subscription.setActivatedAt(LocalDateTime.now());

        Subscription savedSubscription = subscriptionRepository.save(subscription);

        SubscriptionActivatedEvent activatedEvent = new SubscriptionActivatedEvent(
                UUID.randomUUID(),
                "SubscriptionActivated",
                savedSubscription.getOrderId(),
                savedSubscription.getId(),
                savedSubscription.getCustomerId(),
                savedSubscription.getTariffCode(),
                savedSubscription.getMsisdn(),
                event.minutesIncluded(),
                event.smsIncluded(),
                event.dataMbIncluded(),
                savedSubscription.getActivatedAt());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(activatedEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SubscriptionActivated event", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(savedSubscription.getId());
        outboxEvent.setAggregateType("SUBSCRIPTION");
        outboxEvent.setEventType("SubscriptionActivated");
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);

        saveProcessedEvent(event);
        LOGGER.info("Subscription created and event marked processed eventId={} subscriptionId={}",
                event.eventId(), savedSubscription.getId());
    }

    private void saveProcessedEvent(PaymentCompletedEvent event) {
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEventRepository.save(processedEvent);
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
