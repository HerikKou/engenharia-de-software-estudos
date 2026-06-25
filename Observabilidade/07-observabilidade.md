# 07 · Observabilidade

Um sistema que você não consegue observar é um sistema que você não consegue operar. Observabilidade é a capacidade de entender o estado interno de um sistema a partir de suas saídas — logs, métricas e traces. Este módulo cobre os três pilares e as ferramentas que os sustentam.

---

## Os Três Pilares da Observabilidade

```
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│    Logs     │   │   Métricas  │   │   Traces    │
│             │   │             │   │             │
│ O que       │   │ Quanto /    │   │ Como a      │
│ aconteceu   │   │ Quão rápido │   │ requisição  │
│             │   │             │   │ percorreu   │
└─────────────┘   └─────────────┘   └─────────────┘
```

| Pilar       | Responde                    | Exemplos                                  |
|-------------|-----------------------------|-------------------------------------------|
| **Logs**    | O que aconteceu?            | Erro ao processar transação X; usuário Y logou |
| **Métricas** | Quanto? Com que frequência? | 1200 req/s; latência p99 = 45ms; erro rate = 0.1% |
| **Traces**  | Onde a latência está?       | Requisição levou 300ms: 20ms no DB, 280ms na API externa |

---

## Logs Estruturados

Logs em texto livre são impossíveis de analisar em escala. Logs estruturados (JSON) são indexáveis e permitem queries poderosas.

### Padrões e Níveis

| Nível     | Quando usar                                           |
|-----------|-------------------------------------------------------|
| `TRACE`   | Detalhe máximo — execução linha a linha. Apenas em desenvolvimento |
| `DEBUG`   | Informações de diagnóstico. Útil em homologação       |
| `INFO`    | Eventos normais do ciclo de vida (transação criada, serviço iniciado) |
| `WARN`    | Situação anormal mas recuperável (retry, fallback ativado) |
| `ERROR`   | Falha que impacta a operação mas o sistema continua   |
| `FATAL`   | Falha crítica — sistema não consegue continuar        |

**Regra:** em produção, nível mínimo `INFO`. Não logar `DEBUG` em produção — volume excessivo degrada performance e dificulta análise.

---

### Configuração com Logback + JSON

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>transacaoId</includeMdcKeyName>
            <includeMdcKeyName>clienteId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

```java
// Usando no código
@Slf4j
@Service
public class TransacaoService {

    public void processar(TransacaoEvent evento) {
        // Adiciona contexto ao MDC — aparece em todos os logs desta thread
        MDC.put("transacaoId", evento.getId());
        MDC.put("clienteId", evento.getClienteId());

        try {
            log.info("Iniciando processamento da transação");

            // lógica de negócio

            log.info("Transação processada com sucesso. valor={}", evento.getValor());
        } catch (Exception e) {
            log.error("Falha ao processar transação. erro={}", e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear(); // limpa o contexto após o processamento
        }
    }
}
```

**Saída JSON resultante:**

```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.empresa.TransacaoService",
  "message": "Transação processada com sucesso. valor=1500.00",
  "transacaoId": "uuid-123",
  "clienteId": "uuid-456",
  "correlationId": "req-789"
}
```

---

### Correlação de Eventos

Em microserviços, uma operação passa por múltiplos serviços. O `correlationId` é o fio que conecta todos os logs de uma mesma requisição.

```java
// Filter que propaga o correlationId via HTTP header
@Component
public class CorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String correlationId = Optional
            .ofNullable(request.getHeader("X-Correlation-Id"))
            .orElse(UUID.randomUUID().toString());

        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## Métricas

Métricas são medições numéricas coletadas ao longo do tempo. Permitem entender tendências, definir alertas e criar dashboards.

### Tipos de Métrica

| Tipo          | Comportamento                    | Exemplos                                    |
|---------------|----------------------------------|---------------------------------------------|
| **Counter**   | Sempre aumenta                   | Total de requisições, erros, transações     |
| **Gauge**     | Sobe e desce                     | Memória usada, conexões ativas, tamanho da fila |
| **Histogram** | Distribui valores em buckets     | Latência de requisições, tamanho de payload |
| **Summary**   | Percentis pré-calculados         | p50, p95, p99 de latência                   |

---

### Micrometer + Spring Boot

Spring Boot Actuator + Micrometer expõem métricas automaticamente no endpoint `/actuator/metrics`.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${APP_ENV:local}
```

```java
// Métricas customizadas
@Service
public class TransacaoService {

    private final MeterRegistry meterRegistry;

    public void processar(TransacaoEvent evento) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // lógica de negócio

            meterRegistry.counter("transacoes.processadas",
                "status", "sucesso",
                "metodo", evento.getMetodo()
            ).increment();

            sample.stop(meterRegistry.timer("transacoes.duracao",
                "metodo", evento.getMetodo()
            ));

        } catch (Exception e) {
            meterRegistry.counter("transacoes.processadas",
                "status", "erro"
            ).increment();
            throw e;
        }
    }
}
```

---

### Golden Signals

As quatro métricas mais importantes para monitorar qualquer serviço (Google SRE):

| Signal        | O que mede                         | Alerta quando...              |
|---------------|------------------------------------|-------------------------------|
| **Latência**  | Tempo de resposta (p50, p95, p99)  | p99 > SLO definido            |
| **Tráfego**   | Volume de requisições por segundo  | Queda inesperada ou pico      |
| **Erros**     | Taxa de respostas com erro         | Error rate > 1%               |
| **Saturação** | Uso de recursos (CPU, memória, DB) | CPU > 80% por mais de 5 min   |

---

## Monitoramento de Aplicações

### Stack Comum

```
Aplicação → [Prometheus] → [Grafana]
              (coleta)       (dashboards)

Aplicação → [Loki] → [Grafana]
              (logs)    (exploração)

Aplicação → [Jaeger/Zipkin] → [Grafana]
              (traces)          (traces)
```

### Comparativo de Ferramentas

| Ferramenta   | Tipo         | Vantagens                                               | Desvantagens                                   |
|--------------|--------------|---------------------------------------------------------|------------------------------------------------|
| **Prometheus** | Métricas   | Open source, modelo pull eficiente, ótima integração Spring | Retenção limitada, sem logs/traces nativos |
| **Grafana**  | Dashboards   | Múltiplas fontes de dados, alertas, comunidade enorme   | Apenas visualização — não coleta dados         |
| **Loki**     | Logs         | Indexação leve (só labels), integra com Grafana         | Queries menos poderosas que Elasticsearch      |
| **Jaeger**   | Traces       | Open source, compatível com OpenTelemetry               | Interface menos polida, setup mais trabalhoso  |
| **Datadog**  | Tudo (APM)   | Logs + métricas + traces correlacionados, fácil setup, APM automático | Custo alto em escala, vendor lock-in |
| **ELK Stack**| Logs/busca   | Busca full-text poderosa, Kibana flexível                | Operacionalmente complexo, consumo alto de RAM |

### Datadog

Plataforma de observabilidade unificada — logs, métricas, traces e APM em um único lugar.

```yaml
# docker-compose.yml — Datadog Agent
datadog-agent:
  image: datadog/agent:latest
  environment:
    - DD_API_KEY=${DATADOG_API_KEY}
    - DD_SITE=datadoghq.com
    - DD_APM_ENABLED=true
    - DD_LOGS_ENABLED=true
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock
```

```yaml
# Configuração do serviço Spring Boot para APM
environment:
  - JAVA_OPTS=-javaagent:/dd-java-agent.jar
  - DD_SERVICE=transacao-service
  - DD_ENV=production
  - DD_VERSION=1.2.0
  - DD_TRACE_SAMPLE_RATE=1.0
```

**O que o APM do Datadog oferece:**
- Traces automáticos de requisições HTTP, queries SQL, chamadas Kafka
- Flame graphs de latência por serviço
- Service Map — visualização das dependências entre serviços
- Correlação automática entre traces, logs e métricas

---

### Health Checks

Spring Boot Actuator expõe `/actuator/health` automaticamente, verificando banco de dados, Kafka, Redis e outros componentes.

```java
// Health check customizado
@Component
public class TransacaoQueueHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        long pendentes = repository.countByStatus(StatusTransacao.PENDENTE);

        if (pendentes > 10_000) {
            return Health.down()
                .withDetail("pendentes", pendentes)
                .withDetail("mensagem", "Fila acima do limite")
                .build();
        }

        return Health.up()
            .withDetail("pendentes", pendentes)
            .build();
    }
}
```

---

## Referências

- [Micrometer Documentation](https://micrometer.io/docs) — referência oficial
- [Datadog APM for Java](https://docs.datadoghq.com/tracing/trace_collection/automatic_instrumentation/dd_libraries/java/) — setup com dd-java-agent
- *Site Reliability Engineering* — Google (disponível em [sre.google](https://sre.google/books/))
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) — documentação oficial

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
