# 03 · Arquitetura de Software

Arquitetura é a soma das decisões difíceis de reverter. Este módulo cobre os padrões e abordagens que definem como sistemas são estruturados — desde a escolha entre monólito e microserviços até os princípios que tornam o código testável e evolutivo.

---

## Monólito vs Microserviços

### Monólito

Uma única unidade deployável que contém toda a lógica da aplicação: camada de apresentação, regras de negócio e acesso a dados.

```
┌─────────────────────────────────────┐
│           Aplicação Monolítica       │
│  ┌──────────┐  ┌──────────────────┐ │
│  │  Módulo  │  │     Módulo       │ │
│  │  Clientes│  │   Transações     │ │
│  └──────────┘  └──────────────────┘ │
│  ┌──────────────────────────────┐   │
│  │        Banco de Dados        │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

**Vantagens:**
- Desenvolvimento inicial mais rápido
- Deploy simples (um artefato)
- Debugging e rastreamento diretos
- Sem latência de rede entre módulos
- Transações ACID entre domínios sem esforço extra

**Desvantagens:**
- Acoplamento cresce com o tempo
- Deploy de qualquer mudança afeta tudo
- Escala como unidade — não é possível escalar só o módulo de maior demanda
- Adotar novas tecnologias em partes isoladas é difícil

**Quando usar:** projetos novos com equipes pequenas, MVPs, sistemas onde a complexidade do domínio não justifica a sobrecarga operacional de microserviços.

---

### Microserviços

Sistema decomposto em serviços independentes, cada um com sua própria responsabilidade, banco de dados e ciclo de deploy.

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Transação   │    │  Devolução   │    │    Score     │
│   Service    │───▶│   Service    │───▶│   Service    │
│  :8081       │    │  :8082       │    │  :8083       │
│  [Postgres]  │    │  [Postgres]  │    │  [Postgres]  │
└──────────────┘    └──────────────┘    └──────────────┘
        │                  │                   │
        └──────────────────┴───────────────────┘
                           │
                       [Kafka]
```

**Vantagens:**
- Deploy independente por serviço
- Escalabilidade granular (só o serviço sob pressão)
- Isolamento de falha — um serviço falhando não derruba os outros
- Times autônomos por domínio
- Liberdade de escolha tecnológica por serviço

**Desvantagens:**
- Complexidade operacional alta (observabilidade, service discovery, rede)
- Transações distribuídas são difíceis (sem ACID cross-service)
- Latência de rede entre serviços
- Overhead de infraestrutura (múltiplos bancos, containers, filas)

**Quando usar:** sistemas com múltiplos domínios bem definidos, equipes grandes trabalhando em paralelo, requisitos de escala diferenciados por módulo, necessidade de deploys frequentes e independentes.

---

### A Regra Prática

> Comece com um monólito bem estruturado (modular). Extraia microserviços quando você tiver uma razão concreta — não como antecipação.

Martin Fowler chama isso de **"Monolith First"**: extrair microserviços antes de entender as fronteiras do domínio resulta em microserviços mal delimitados, que são piores que um monólito.

---

## Separação de Responsabilidades

### Camadas (Layered Architecture)

O padrão mais comum em aplicações Spring Boot.

```
Controller  →  Service  →  Repository  →  Database
   (HTTP)     (negócio)    (persistência)
```

**Regra:** cada camada só se comunica com a camada imediatamente abaixo. O Controller nunca acessa o Repository diretamente.

```java
// Controller: recebe HTTP, delega ao Service
@RestController
@RequestMapping("/transacoes")
public class TransacaoController {
    private final TransacaoService service;

    @PostMapping
    public ResponseEntity<TransacaoResponse> criar(@RequestBody TransacaoRequest request) {
        return ResponseEntity.ok(service.processar(request));
    }
}

// Service: regras de negócio, orquestração
@Service
public class TransacaoService {
    private final TransacaoRepository repository;

    public TransacaoResponse processar(TransacaoRequest request) {
        validar(request);
        Transacao entidade = repository.save(request.toEntity());
        return TransacaoResponse.from(entidade);
    }
}

// Repository: acesso a dados
public interface TransacaoRepository extends JpaRepository<Transacao, UUID> { }
```

---

### Bounded Context

Conceito do Domain-Driven Design (DDD): cada subdomínio tem seu próprio modelo, vocabulário e fronteiras bem definidas.

```
┌─────────────────────┐    ┌─────────────────────┐
│  Contexto: Pagamento│    │  Contexto: Fraude   │
│                     │    │                     │
│  Transacao          │    │  Transacao          │
│  (valor, destino,   │    │  (score, risco,     │
│   método)           │    │   padrão)           │
└─────────────────────┘    └─────────────────────┘
```

> O mesmo nome ("Transacao") pode ter significados diferentes em contextos diferentes. Isso é intencional — cada contexto modela o que precisa, sem acoplamento ao modelo de outro domínio.

---

## Clean Architecture

Proposta por Robert C. Martin (Uncle Bob). O princípio central: **as dependências sempre apontam para dentro** — regras de negócio não dependem de frameworks, bancos ou interfaces externas.

```
┌─────────────────────────────────────────┐
│            Frameworks & Drivers          │  ← Spring, JPA, Kafka
│  ┌──────────────────────────────────┐   │
│  │      Interface Adapters          │   │  ← Controllers, Gateways
│  │  ┌───────────────────────────┐   │   │
│  │  │     Application Layer     │   │   │  ← Use Cases / Services
│  │  │  ┌─────────────────────┐  │   │   │
│  │  │  │    Domain / Entities │  │   │   │  ← Entidades puras, regras
│  │  │  └─────────────────────┘  │   │   │
│  │  └───────────────────────────┘   │   │
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

**Regra da dependência:** código no círculo interno nunca importa nada do círculo externo.

**Na prática em Spring Boot:**

```java
// Domain (sem dependência de framework)
public class Transacao {
    private UUID id;
    private BigDecimal valor;
    private StatusTransacao status;

    public void aprovar() {
        if (this.status != StatusTransacao.PENDENTE) {
            throw new IllegalStateException("Transação não está pendente");
        }
        this.status = StatusTransacao.APROVADA;
    }
}

// Use Case (depende só do domínio)
public class AprovarTransacaoUseCase {
    private final TransacaoGateway gateway; // interface, não implementação

    public void executar(UUID id) {
        Transacao transacao = gateway.buscarPorId(id);
        transacao.aprovar();
        gateway.salvar(transacao);
    }
}

// Gateway (interface definida no domínio, implementada fora)
public interface TransacaoGateway {
    Transacao buscarPorId(UUID id);
    void salvar(Transacao transacao);
}
```

---

## API Gateway

Em arquiteturas de microserviços, o API Gateway é o ponto de entrada único para clientes externos. Ele centraliza responsabilidades que seriam repetidas em cada serviço.

```
Cliente HTTP
     │
     ▼
┌──────────────────────────────┐
│          API Gateway         │
│  - Autenticação (JWT/OAuth2) │
│  - Rate Limiting             │
│  - Roteamento                │
│  - Load Balancing            │
│  - Logging centralizado      │
└──────────────────────────────┘
     │          │          │
     ▼          ▼          ▼
 Serviço A  Serviço B  Serviço C
```

**Responsabilidades típicas:**
- **Roteamento:** `/api/transacoes/**` → TransacaoService; `/api/devolucoes/**` → DevolucaoService
- **Autenticação:** valida JWT antes de encaminhar a requisição
- **Rate limiting:** limita requisições por cliente/IP
- **Circuit breaker:** para de encaminhar requisições a um serviço que está falhando

**Ferramentas:** Spring Cloud Gateway, Kong, NGINX, AWS API Gateway

> **Atenção:** o Gateway não deve conter lógica de negócio. Ele é infraestrutura — roteamento e cross-cutting concerns apenas.

---

## Referências

- *Clean Architecture* — Robert C. Martin
- *Building Microservices* — Sam Newman
- *Domain-Driven Design* — Eric Evans
- [Microservices.io](https://microservices.io) — padrões de microserviços com exemplos

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
