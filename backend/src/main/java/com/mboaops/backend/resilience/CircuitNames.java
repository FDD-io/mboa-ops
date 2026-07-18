package com.mboaops.backend.resilience;

import java.util.List;

/**
 * Noms des circuit breakers de MBOA-OPS. Toute création de circuit passe
 * par ces constantes pour que le health endpoint et le kill switch chaos
 * voient exactement le même ensemble.
 */
public final class CircuitNames {

    public static final String QWEN = "qwen";
    public static final String WHATSAPP = "whatsapp";
    public static final String SMS = "sms";
    public static final String STOCK = "stock";

    public static final List<String> TOUS = List.of(QWEN, WHATSAPP, SMS, STOCK);

    private CircuitNames() {
    }
}
