package com.turkcell.billing_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    private BigDecimal defaultMonthlyFee = new BigDecimal("149.90");
    private BigDecimal taxRate = new BigDecimal("0.20");
    private Overage overage = new Overage();
    private Map<String, BigDecimal> tariffs = new HashMap<>();

    public BigDecimal getDefaultMonthlyFee() { return defaultMonthlyFee; }
    public void setDefaultMonthlyFee(BigDecimal defaultMonthlyFee) { this.defaultMonthlyFee = defaultMonthlyFee; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public Overage getOverage() { return overage; }
    public void setOverage(Overage overage) { this.overage = overage; }

    public Map<String, BigDecimal> getTariffs() { return tariffs; }
    public void setTariffs(Map<String, BigDecimal> tariffs) { this.tariffs = tariffs; }

    public BigDecimal resolveMonthlyFee(String tariffCode) {
        if (tariffCode != null && tariffs.containsKey(tariffCode)) {
            return tariffs.get(tariffCode);
        }
        return defaultMonthlyFee;
    }

    public static class Overage {
        private BigDecimal voicePerMinute = new BigDecimal("0.50");
        private BigDecimal smsPerUnit = new BigDecimal("0.25");
        private BigDecimal dataPerMb = new BigDecimal("0.10");

        public BigDecimal getVoicePerMinute() { return voicePerMinute; }
        public void setVoicePerMinute(BigDecimal voicePerMinute) { this.voicePerMinute = voicePerMinute; }

        public BigDecimal getSmsPerUnit() { return smsPerUnit; }
        public void setSmsPerUnit(BigDecimal smsPerUnit) { this.smsPerUnit = smsPerUnit; }

        public BigDecimal getDataPerMb() { return dataPerMb; }
        public void setDataPerMb(BigDecimal dataPerMb) { this.dataPerMb = dataPerMb; }
    }
}
