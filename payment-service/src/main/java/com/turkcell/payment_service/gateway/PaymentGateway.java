package com.turkcell.payment_service.gateway;

import java.math.BigDecimal;

/**
 * Odeme saglayicisi soyutlamasi. Gercek PSP entegrasyonu yerine mock implementasyon
 * kullanilir; testlerde mock'lanabilmesi icin arayuz olarak tanimlanmistir.
 */
public interface PaymentGateway {

    boolean charge(String method, BigDecimal amount);
}
