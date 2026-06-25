# 05 · Sistemas Distribuídos

Quando um único serviço não é suficiente, os componentes precisam se comunicar de forma confiável através da rede. Este módulo cobre os fundamentos de comunicação assíncrona, as ferramentas que a viabilizam — Kafka e RabbitMQ — e os padrões que tornam sistemas distribuídos resilientes.

---

## Por que Comunicação Assíncrona?

Em sistemas síncronos, o produtor espera o consumidor responder antes de continuar. Em sistemas assíncronos, o produtor envia a mensagem e segue em frente — o consumidor processará quando puder.

**Comparação:**

| Critério                  | Síncrono (REST/gRPC)        | Assíncrono (Kafka/RabbitMQ)     |
|---------------------------|-----------------------------|---------------------------------|
| Acoplamento               | Alto (A depende de B estar up)| Baixo (via broker intermediário)|
| Latência percebida        | Bloqueante                  | Não bloqueante                  |
| Resiliência a falhas      | Falha de B impacta A        | B pode falhar e processar depois|
| Throughput                | Limitado pela cadeia        | Alta capacidade de buffer       |
| Rastreabilidade           | Direta                      | Requer correlação de eventos    |
| Consistência              | Forte (imediata)            | Eventual                        |

**Regra de ouro:** use comunicação síncrona quando precisa da resposta para continuar. Use comunicação assíncrona quando pode continuar sem ela.

---

## Apache Kafka

Kafka é uma plataforma de streaming de eventos distribuída. Projetada para alto throughput, baixa latência e retenção de mensagens.

### Conceitos Fundamentais

```
Producer ──▶ [Topic: transacoes] ──▶ Consumer Group A
                     │
                     └────────────▶ Consumer Group B
```

| Conceito         | Definição                                                                 |
|------------------|---------------------------------------------------------------------------|
| **Topic**        | Canal lógico de mensagens. Análogo a uma "fila com histórico"             |
| **Partition**    | Subdivisão de um topic. Permite paralelismo. Mensagens ordenadas por partição |
| **Producer**     | Publica mensagens em um topic                                             |
| **Consumer**     | Lê mensagens de um topic. Mantém seu próprio offset                       |
| **Consumer Group** | Grupo de consumers que divide as partições de um topic entre si        |
| **Offset**       | Posição do consumer dentro de uma partição (qual mensagem já foi lida)    |
| **Broker**       | Servidor Kafka que armazena os dados                                      |
| **Retention**    | Quanto tempo Kafka guarda as mensagens (independente de ter sido lida)    |

---

### Particionamento e Paralelismo

```
Topic: pagamentos (3 partições)
┌─────────────────────────────────────────────┐
│ Partition 0: [msg1] [msg4] [msg7]           │
│ Partition 1: [msg2] [msg5] [msg8]           │
│ Partition 2: [msg3] [msg6] [msg9]           │
└─────────────────────────────────────────────┘

Consumer Group: payment-processors (3 instances)
- Instance A → Partition 0
- Instance B → Partition 1
- Instance C → Partition 2
```

> **Regra:** o número máximo de consumers ativos em um grupo é igual ao número de partições. Consumers a mais ficam ociosos.

**Chave de partição:** garante que mensagens relacionadas vão para a mesma partição (e portanto são processadas em ordem pelo mesmo consumer).

```java
// Mensagens do mesmo cliente sempre na mesma partição
producer.send(new ProducerRecord<>("transacoes", clienteId, evento));
```

---

### Producer e Consumer em Spring Boot

```java
// Publicando evento
@Service
public class TransacaoProducer {
    private final KafkaTemplate<String, TransacaoEvent> kafkaTemplate;

    public void publicar(TransacaoEvent evento) {
        kafkaTemplate.send("transacoes.criadas", evento.getTransacaoId(), evento);
    }
}

// Consumindo evento
@Component
public class TransacaoConsumer {

    @KafkaListener(topics = "transacoes.criadas", groupId = "score-service")
    public void processar(TransacaoEvent evento) {
        scoreService.calcular(evento);
    }
}
```

---

### Retry e Tratamento de Erros

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    // Tenta 3 vezes com intervalo de 1 segundo
    FixedBackOff backOff = new FixedBackOff(1000L, 3L);

    // Após as tentativas, envia para o Dead Letter Topic
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(template);

    return new DefaultErrorHandler(recoverer, backOff);
}
```

**Dead Letter Topic (DLT):** mensagens que falharam após todas as tentativas de retry são movidas para um topic separado (`.DLT`). Permitem análise e reprocessamento manual sem bloquear o fluxo principal.

---

### Idempotência no Consumer

Kafka entrega "at-least-once" por padrão — em caso de falha, a mesma mensagem pode ser reprocessada. O consumer deve ser idempotente.

```java
@KafkaListener(topics = "pagamentos.processados")
public void processar(PagamentoEvent evento) {
    // Verifica se já processou este evento antes
    if (repository.existsByIdempotencyKey(evento.getIdempotencyKey())) {
        log.info("Evento já processado: {}", evento.getIdempotencyKey());
        return;
    }

    processar(evento);
}
```

---

## RabbitMQ

RabbitMQ é um message broker baseado no protocolo AMQP. Diferente do Kafka, foca em roteamento flexível de mensagens e entrega garantida.

### Conceitos Fundamentais

```
Producer ──▶ Exchange ──▶ Queue ──▶ Consumer
                │
                └──▶ Queue 2 ──▶ Consumer 2
```

| Conceito     | Definição                                                              |
|--------------|------------------------------------------------------------------------|
| **Exchange** | Recebe mensagens do producer e as roteia para filas com base em regras |
| **Queue**    | Buffer que armazena mensagens até o consumer processá-las              |
| **Binding**  | Regra que conecta Exchange à Queue (pode usar routing key ou pattern)  |
| **Routing Key** | Critério de roteamento (como uma etiqueta na mensagem)              |

---

### Tipos de Exchange

**Direct Exchange:** roteia pela routing key exata.

```
Exchange(direct) ──"pagamento.aprovado"──▶ Queue: notificacoes-email
                 ──"pagamento.aprovado"──▶ Queue: atualiza-saldo
```

**Topic Exchange:** roteia por padrão com wildcards (`*` = uma palavra, `#` = zero ou mais).

```
Exchange(topic) ──"pagamento.*"──▶ Queue: auditoria-pagamentos
                ──"*.aprovado"──▶  Queue: aprovacoes
```

**Fanout Exchange:** replica para todas as filas vinculadas, sem filtro.

```
Exchange(fanout) ──▶ Queue: servico-A
                 ──▶ Queue: servico-B
                 ──▶ Queue: servico-C
```

---

### Kafka vs RabbitMQ

| Critério               | Kafka                            | RabbitMQ                         |
|------------------------|----------------------------------|----------------------------------|
| Modelo                 | Log distribuído                  | Message broker (AMQP)            |
| Retenção               | Persistente (configurável)       | Mensagem removida após consumo   |
| Replay de eventos      | Sim (por offset)                 | Não (nativo)                     |
| Roteamento             | Simples (por topic/partição)     | Flexível (exchanges, bindings)   |
| Throughput             | Muito alto (milhões/s)           | Alto (dezenas de milhares/s)     |
| Ordering               | Por partição                     | Por fila (sem paralelismo)       |
| Casos de uso           | Streaming, event sourcing, logs  | Task queues, RPC, roteamento     |

**Regra geral:**
- **Kafka:** quando você precisa de alto volume, replay de eventos, ou múltiplos consumers independentes lendo o mesmo dado
- **RabbitMQ:** quando você precisa de roteamento flexível, entrega garantida com ACK, ou integração com sistemas legados

---

## Event-Driven Architecture

Sistemas orientados a eventos são desacoplados por natureza — nenhum serviço sabe quem vai consumir os eventos que ele produz.

### Padrões Fundamentais

**Event Notification:** serviço publica evento informando que algo aconteceu. Outros serviços reagem se quiserem.

```
TransacaoService ──▶ [transacao.criada] ──▶ ScoreService
                                        ──▶ NotificacaoService
                                        ──▶ AuditoriaService
```

**Event-Carried State Transfer:** o evento carrega todos os dados necessários — consumers não precisam chamar o produtor para obter informações adicionais.

```json
{
  "eventType": "TRANSACAO_CRIADA",
  "transacaoId": "uuid-123",
  "clienteId": "uuid-456",
  "valor": 1500.00,
  "chavePixDestino": "email@exemplo.com",
  "criadaEm": "2024-01-15T10:30:00Z"
}
```

**Saga Pattern:** coordena transações distribuídas sem 2PC (Two-Phase Commit). Cada serviço executa sua parte e, em caso de falha, publica um evento de compensação.

```
1. PedidoService  → publica PedidoCriado
2. PagamentoService → reserva saldo → publica PagamentoReservado
3. EstoqueService → reserva item → publica EstoqueReservado
4. PedidoService  → confirma pedido

Em falha no passo 3:
3b. EstoqueService → publica EstoqueIndisponivel
2b. PagamentoService → estorna reserva → publica PagamentoEstornado
```

---

### Correlação de Eventos

Em sistemas assíncronos, é fundamental poder rastrear o fluxo de uma operação através de múltiplos serviços.

```java
// Sempre propague o correlationId nos eventos
public class TransacaoEvent {
    private String correlationId;  // mesmo ID em todos os eventos do fluxo
    private String transacaoId;
    private String eventType;
    // ...
}

// No consumer, inclua o correlationId nos logs
@KafkaListener(topics = "transacoes.criadas")
public void processar(TransacaoEvent evento) {
    MDC.put("correlationId", evento.getCorrelationId());
    log.info("Processando transação: {}", evento.getTransacaoId());
    // ...
}
```

---

## Referências

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/) — referência oficial
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials) — tutoriais por exchange type
- *Designing Data-Intensive Applications* — Martin Kleppmann (capítulos sobre streams)
- [Microservices.io — Saga Pattern](https://microservices.io/patterns/data/saga.html)

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
