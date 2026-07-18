# Preuves — 3 scénarios de bout en bout

Chaînes d'événements réelles (event store append-only, `GET /api/events/{commandeId}`),
capturées le 2026-07-19 sur le pipeline complet avec appels Qwen réels
(`qwen3.6-flash` routage/extraction, `qwen3.7-max` règles métier) :

`MESSAGE_RECU → RouterAgent → extraction → fusion → BusinessRulesAgent (stock + crédit) → politique HITL`

Politique HITL : confiance > 0.9 et action réversible → auto ; 0.6–0.9 ou action
irréversible (crédit en cours) → DecisionCard ; < 0.6 → escalade. Si un circuit
breaker est ouvert, la confiance est multipliée par 0.7 avant application des seuils.

---

## Scénario 1 — Client sans crédit, stock OK → auto-approbation + devis

**Requête** : `POST /api/messages`
```json
{"clientPhone": "+237699112233", "type": "TEXT", "content": "Bonjour, je veux 2 sacs de ciment et 10 savons svp"}
```

**Réponse** (`201`) — aucune DecisionCard, devis envoyé automatiquement :
```json
{
  "commandeId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
  "eventId": "ef24b50c-02e2-4869-ada6-86acc5a39c66",
  "statut": "DEVIS_ENVOYE",
  "clarification": null,
  "intention": "COMMANDE",
  "decision": "AUTO_APPROVE",
  "decisionCardId": null
}
```

**Chaîne d'événements complète** :
```json
[
  {
    "id": "ef24b50c-02e2-4869-ada6-86acc5a39c66",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "MESSAGE_RECU",
    "payload": {
      "type": "TEXT",
      "content": "Bonjour, je veux 2 sacs de ciment et 10 savons svp",
      "clientPhone": "+237699112233"
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:33:45.935329Z"
  },
  {
    "id": "1f2bbcc4-bb25-4d4b-b184-2b18b93bb5e6",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "ROUTER_CLASSIFICATION",
    "payload": {
      "langue": "FR",
      "urgence": 2,
      "intention": "COMMANDE",
      "confidence": 0.95
    },
    "confidence": 0.95,
    "reasoning": "{\"intention\": \"COMMANDE\", \"urgence\": 2, \"langue\": \"FR\", \"confidence\": 0.95}",
    "createdAt": "2026-07-18T23:33:52.568915Z"
  },
  {
    "id": "7584db0f-3c14-4cb0-a237-a5f7ca1aedf1",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "EXTRACTION_TEXTE",
    "payload": [
      {
        "produit": "ciment",
        "quantite": 2,
        "confidence": 0.95
      },
      {
        "produit": "savon",
        "quantite": 10,
        "confidence": 0.95
      }
    ],
    "confidence": 0.95,
    "reasoning": "[{\"produit\": \"ciment\", \"quantite\": 2, \"confidence\": 0.95}, {\"produit\": \"savon\", \"quantite\": 10, \"confidence\": 0.95}]",
    "createdAt": "2026-07-18T23:34:00.031985Z"
  },
  {
    "id": "edd985d4-3391-4f3d-a21c-0f102faf9e4a",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "FUSION_REUSSIE",
    "payload": [
      {
        "produit": "ciment",
        "quantite": 2,
        "confidence": 0.95
      },
      {
        "produit": "savon",
        "quantite": 10,
        "confidence": 0.95
      }
    ],
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:34:00.088560Z"
  },
  {
    "id": "30eb8ed0-db14-4ac3-8cea-ce67cca934aa",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "BUSINESS_RULES_DECISION",
    "payload": {
      "decision": "AUTO_APPROVE",
      "reasoning": "Le crÃ©dit en cours est de 0 FCFA (infÃ©rieur Ã  30000 FCFA) et le stock est suffisant pour toutes les lignes (ciment: 2/150, savon: 10/300).",
      "confidence": 1.0,
      "proposition": ""
    },
    "confidence": 1.0,
    "reasoning": "{\"decision\": \"AUTO_APPROVE\", \"confidence\": 1.0, \"reasoning\": \"Le crÃ©dit en cours est de 0 FCFA (infÃ©rieur Ã  30000 FCFA) et le stock est suffisant pour toutes les lignes (ciment: 2/150, savon: 10/300).\", \"proposition\": \"\"}",
    "createdAt": "2026-07-18T23:34:24.227502Z"
  },
  {
    "id": "73bb37df-da2d-4e93-b8f9-f0dc93e128a1",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "ORCHESTRATION_AUTO",
    "payload": {
      "decision": "AUTO_APPROVE",
      "reasoning": "Le crÃ©dit en cours est de 0 FCFA (infÃ©rieur Ã  30000 FCFA) et le stock est suffisant pour toutes les lignes (ciment: 2/150, savon: 10/300).",
      "confidence": 1.0,
      "proposition": ""
    },
    "confidence": 1.0,
    "reasoning": "Le crÃ©dit en cours est de 0 FCFA (infÃ©rieur Ã  30000 FCFA) et le stock est suffisant pour toutes les lignes (ciment: 2/150, savon: 10/300).",
    "createdAt": "2026-07-18T23:34:24.241320Z"
  },
  {
    "id": "fb243efa-6a4a-4b18-8829-b1eb91e01565",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "DEVIS_GENERE",
    "payload": {
      "devis": "Devis MBOA-OPS\n- Ciment CIMENCAM (sac 50kg) x 2 = 13000.00 FCFA\n- Savon de Marseille (barre) x 10 = 5000.00 FCFA\nTotal : 18000.00 FCFA",
      "montantTotal": 18000.0
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:34:24.261362Z"
  },
  {
    "id": "d3fdb0ed-1410-4b7a-90a9-dc615db63a85",
    "aggregateId": "b68d5163-28da-4684-addc-9e27e2e94e9e",
    "type": "NOTIFICATION_ENVOYEE",
    "payload": {
      "canal": "WHATSAPP",
      "telephone": "+237699112233"
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:34:24.271085Z"
  }
]
```

---

## Scénario 2 — Mme Ngo (crédit 35 000 FCFA, 12 commandes, 0 défaut) → DecisionCard + acompte 50 %

**Requête** : `POST /api/messages`
```json
{"clientPhone": "+237677123456", "type": "TEXT", "content": "Bonjour, je veux 10 toles ondulees pour ma toiture"}
```

**Réponse** (`201`) — crédit > 30 000 mais historique fiable : le modèle propose
un acompte 50 % Mobile Money ; action irréversible (crédit en cours) → DecisionCard :
```json
{
  "commandeId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
  "eventId": "17169f03-f9f3-41cf-a740-03cd7c54a856",
  "statut": "EN_ATTENTE_PATRON",
  "clarification": null,
  "intention": "COMMANDE",
  "decision": "CLARIFY_CLIENT",
  "decisionCardId": "862872b2-a774-4926-8158-9c6bd81ee169"
}
```

**Décision du patron** : `POST /api/decisions/862872b2-a774-4926-8158-9c6bd81ee169/respond`
```json
{"action": "APPROVE", "commentaire": "OK avec acompte 50% Mobile Money confirme au telephone"}
```

**Réponse** (`200`) — la commande avance jusqu'au devis :
```json
{
  "decisionCardId": "862872b2-a774-4926-8158-9c6bd81ee169",
  "action": "APPROVE",
  "commandeStatut": "DEVIS_ENVOYE"
}
```

**Chaîne d'événements complète** :
```json
[
  {
    "id": "17169f03-f9f3-41cf-a740-03cd7c54a856",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "MESSAGE_RECU",
    "payload": {
      "type": "TEXT",
      "content": "Bonjour, je veux 10 toles ondulees pour ma toiture",
      "clientPhone": "+237677123456"
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:34:32.267941Z"
  },
  {
    "id": "e25bbc6b-1927-437b-a019-848275b85321",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "ROUTER_CLASSIFICATION",
    "payload": {
      "langue": "FR",
      "urgence": 2,
      "intention": "COMMANDE",
      "confidence": 0.95
    },
    "confidence": 0.95,
    "reasoning": "{\"intention\": \"COMMANDE\", \"urgence\": 2, \"langue\": \"FR\", \"confidence\": 0.95}",
    "createdAt": "2026-07-18T23:34:36.137745Z"
  },
  {
    "id": "11fc3cac-4213-4a95-9f78-1e63d8f54057",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "EXTRACTION_TEXTE",
    "payload": [
      {
        "produit": "tôle ondulée",
        "quantite": 10,
        "confidence": 0.95
      }
    ],
    "confidence": 0.95,
    "reasoning": "[{\"produit\": \"tôle ondulée\", \"quantite\": 10, \"confidence\": 0.95}]",
    "createdAt": "2026-07-18T23:34:49.990639Z"
  },
  {
    "id": "cc57d0f7-4c5e-458e-97c5-f35aa7078732",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "FUSION_REUSSIE",
    "payload": [
      {
        "produit": "tôle ondulée",
        "quantite": 10,
        "confidence": 0.95
      }
    ],
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:34:50.013478Z"
  },
  {
    "id": "6f5b1090-b5aa-4ae3-b397-ba1c5155f424",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "BUSINESS_RULES_DECISION",
    "payload": {
      "decision": "CLARIFY_CLIENT",
      "reasoning": "Le crédit en cours (35000 FCFA) dépasse le seuil de 30000 FCFA. Cependant, le client a un historique fiable (12 commandes, 0 défaut), ce qui active l'exception. Le stock est suffisant pour la ligne (10 demandés, 80 disponibles).",
      "confidence": 0.95,
      "proposition": "Proposer un acompte de 50% via Mobile Money."
    },
    "confidence": 0.95,
    "reasoning": "{\n  \"decision\": \"CLARIFY_CLIENT\",\n  \"confidence\": 0.95,\n  \"reasoning\": \"Le crédit en cours (35000 FCFA) dépasse le seuil de 30000 FCFA. Cependant, le client a un historique fiable (12 commandes, 0 défaut), ce qui active l'exception. Le stock est suffisant pour la ligne (10 demandés, 80 disponibles).\",\n  \"proposition\": \"Proposer un acompte de 50% via Mobile Money.\"\n}",
    "createdAt": "2026-07-18T23:35:18.315158Z"
  },
  {
    "id": "5def2c10-e8ad-4a96-8af8-2cb4d446bf0d",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "DECISION_CARD_CREEE",
    "payload": {
      "decision": "CLARIFY_CLIENT",
      "reasoning": "Le crédit en cours (35000 FCFA) dépasse le seuil de 30000 FCFA. Cependant, le client a un historique fiable (12 commandes, 0 défaut), ce qui active l'exception. Le stock est suffisant pour la ligne (10 demandés, 80 disponibles).",
      "confidence": 0.95,
      "proposition": "Proposer un acompte de 50% via Mobile Money."
    },
    "confidence": 0.95,
    "reasoning": "Le crédit en cours (35000 FCFA) dépasse le seuil de 30000 FCFA. Cependant, le client a un historique fiable (12 commandes, 0 défaut), ce qui active l'exception. Le stock est suffisant pour la ligne (10 demandés, 80 disponibles).",
    "createdAt": "2026-07-18T23:35:18.342480Z"
  },
  {
    "id": "70aca212-04f8-4e63-a569-8774b3c5dd28",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "DECISION_PATRON",
    "payload": {
      "action": "APPROVE",
      "commentaire": "OK avec acompte 50% Mobile Money confirme au telephone",
      "decisionCardId": "862872b2-a774-4926-8158-9c6bd81ee169"
    },
    "confidence": null,
    "reasoning": "OK avec acompte 50% Mobile Money confirme au telephone",
    "createdAt": "2026-07-18T23:35:27.847738Z"
  },
  {
    "id": "b8189489-a00b-4a7c-9f7c-13b5b62d4e9b",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "DEVIS_GENERE",
    "payload": {
      "devis": "Devis MBOA-OPS\n- Tôles ondulées (feuille 2m) x 10 = 45000.00 FCFA\nTotal : 45000.00 FCFA",
      "montantTotal": 45000.0
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:35:27.854366Z"
  },
  {
    "id": "03de934c-5c1c-4e16-bf61-226cfbca7b89",
    "aggregateId": "c6d43141-46fa-46b3-bdc7-607d79eed77b",
    "type": "NOTIFICATION_ENVOYEE",
    "payload": {
      "canal": "WHATSAPP",
      "telephone": "+237677123456"
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:35:27.854884Z"
  }
]
```

---

## Scénario 3 — Circuit stock FORCED_OPEN, même commande que le scénario 1 → dégradation de confiance

**Kill switch** : `POST /api/chaos/stock/toggle` → `{"service": "stock", "etat": "FORCED_OPEN"}`

**Requête** : identique au scénario 1 (même client, même message).

**Réponse** (`201`) — même décision LLM (`AUTO_APPROVE`, confiance 1.0), mais le
circuit stock ouvert dégrade la confiance à 0.7 → DecisionCard au lieu de
l'auto-approbation :
```json
{
  "commandeId": "4cb455ac-463e-4dee-996c-301177c15bdd",
  "eventId": "b877a0d8-3710-42d0-8c94-58b17a8897b6",
  "statut": "EN_ATTENTE_PATRON",
  "clarification": null,
  "intention": "COMMANDE",
  "decision": "AUTO_APPROVE",
  "decisionCardId": "a7e0da52-b1a9-40ce-9c77-7f3c78aa0d4f"
}
```

**Chaîne d'événements complète** (noter `CONFIANCE_DEGRADEE`) :
```json
[
  {
    "id": "b877a0d8-3710-42d0-8c94-58b17a8897b6",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "MESSAGE_RECU",
    "payload": {
      "type": "TEXT",
      "content": "Bonjour, je veux 2 sacs de ciment et 10 savons svp",
      "clientPhone": "+237699112233"
    },
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:35:28.446121Z"
  },
  {
    "id": "678c0c80-e0d2-4ad4-a17f-b27bb96d4a54",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "ROUTER_CLASSIFICATION",
    "payload": {
      "langue": "FR",
      "urgence": 2,
      "intention": "COMMANDE",
      "confidence": 0.95
    },
    "confidence": 0.95,
    "reasoning": "{\"intention\": \"COMMANDE\", \"urgence\": 2, \"langue\": \"FR\", \"confidence\": 0.95}",
    "createdAt": "2026-07-18T23:35:33.422090Z"
  },
  {
    "id": "34a0d6c3-4da0-46da-84a3-d8e5959817cf",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "EXTRACTION_TEXTE",
    "payload": [
      {
        "produit": "ciment",
        "quantite": 2,
        "confidence": 1.0
      },
      {
        "produit": "savon",
        "quantite": 10,
        "confidence": 1.0
      }
    ],
    "confidence": 1.0,
    "reasoning": "[{\"produit\": \"ciment\", \"quantite\": 2, \"confidence\": 1.0}, {\"produit\": \"savon\", \"quantite\": 10, \"confidence\": 1.0}]",
    "createdAt": "2026-07-18T23:35:40.938977Z"
  },
  {
    "id": "3e2eb943-982f-467b-ad52-01ad77341d7c",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "FUSION_REUSSIE",
    "payload": [
      {
        "produit": "ciment",
        "quantite": 2,
        "confidence": 1.0
      },
      {
        "produit": "savon",
        "quantite": 10,
        "confidence": 1.0
      }
    ],
    "confidence": null,
    "reasoning": null,
    "createdAt": "2026-07-18T23:35:40.961607Z"
  },
  {
    "id": "98e5cb05-f64c-4a25-bb32-5314690097ef",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "BUSINESS_RULES_DECISION",
    "payload": {
      "decision": "AUTO_APPROVE",
      "reasoning": "Le crédit en cours est de 0 FCFA, ce qui est inférieur au seuil de 30000 FCFA. Le stock disponible est suffisant pour toutes les lignes de la commande (ciment: 2 demandés pour 150 en stock, savon: 10 demandés pour 300 en stock).",
      "confidence": 1.0,
      "proposition": ""
    },
    "confidence": 1.0,
    "reasoning": "{\"decision\": \"AUTO_APPROVE\", \"confidence\": 1.0, \"reasoning\": \"Le crédit en cours est de 0 FCFA, ce qui est inférieur au seuil de 30000 FCFA. Le stock disponible est suffisant pour toutes les lignes de la commande (ciment: 2 demandés pour 150 en stock, savon: 10 demandés pour 300 en stock).\", \"proposition\": \"\"}",
    "createdAt": "2026-07-18T23:36:03.069385Z"
  },
  {
    "id": "04d014ac-d0b6-4557-b1af-1841e1570828",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "CONFIANCE_DEGRADEE",
    "payload": {
      "circuitsOuverts": [
        "stock"
      ],
      "confidenceDegradee": 0.7,
      "confidenceInitiale": 1.0
    },
    "confidence": 0.7,
    "reasoning": "Circuits ouverts pendant le traitement (stock) : données potentiellement périmées, prudence renforcée",
    "createdAt": "2026-07-18T23:36:03.083882Z"
  },
  {
    "id": "82bd186d-8ab6-4c10-8867-62279a5fe341",
    "aggregateId": "4cb455ac-463e-4dee-996c-301177c15bdd",
    "type": "DECISION_CARD_CREEE",
    "payload": {
      "decision": "AUTO_APPROVE",
      "reasoning": "Le crédit en cours est de 0 FCFA, ce qui est inférieur au seuil de 30000 FCFA. Le stock disponible est suffisant pour toutes les lignes de la commande (ciment: 2 demandés pour 150 en stock, savon: 10 demandés pour 300 en stock).",
      "confidence": 1.0,
      "proposition": ""
    },
    "confidence": 0.7,
    "reasoning": "Le crédit en cours est de 0 FCFA, ce qui est inférieur au seuil de 30000 FCFA. Le stock disponible est suffisant pour toutes les lignes de la commande (ciment: 2 demandés pour 150 en stock, savon: 10 demandés pour 300 en stock).",
    "createdAt": "2026-07-18T23:36:03.098903Z"
  }
]
```

---

## Lecture comparée scénarios 1 et 3

| | Scénario 1 | Scénario 3 |
|---|---|---|
| Décision LLM | AUTO_APPROVE (conf. 1.0) | AUTO_APPROVE (conf. 1.0) |
| Circuit stock | CLOSED | FORCED_OPEN |
| Confiance appliquée | 1.0 | 0.7 (événement `CONFIANCE_DEGRADEE`) |
| Verdict HITL | Auto → devis envoyé | DecisionCard → attente patron |

Même commande, même verdict du modèle : seule la panne du service stock change
l'issue, en poussant la décision vers l'humain (données potentiellement périmées).
