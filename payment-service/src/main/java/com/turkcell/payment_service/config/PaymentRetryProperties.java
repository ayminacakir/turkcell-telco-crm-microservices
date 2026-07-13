package com.turkcell.payment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "payment.retry")
public class PaymentRetryProperties {

    /** FR-27: 24, 72, 168 saat. Dev ortaminda dakika olarak override edilebilir. */
    private List<Integer> delayHours = List.of(24, 72, 168);
    private boolean useMinutesForDev = false;

    public List<Integer> getDelayHours() { return delayHours; }
    public void setDelayHours(List<Integer> delayHours) { this.delayHours = delayHours; }

    public boolean isUseMinutesForDev() { return useMinutesForDev; }
    public void setUseMinutesForDev(boolean useMinutesForDev) { this.useMinutesForDev = useMinutesForDev; }
}
