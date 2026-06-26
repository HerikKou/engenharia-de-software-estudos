# 09 · AWS — Serviços Essenciais

A AWS oferece dezenas de serviços, mas para a maioria dos sistemas backend um conjunto reduzido já resolve grande parte dos problemas: computação (EC2, ECS, Lambda), mensageria (SNS, SQS), dados (RDS, DynamoDB), machine learning (SageMaker) e observabilidade (CloudWatch). Este módulo cobre vantagens, desvantagens e comparações entre eles.

---

## EC2 (Elastic Compute Cloud)

Máquinas virtuais sob demanda. Você escolhe o sistema operacional, instala o que quiser e gerencia o servidor como se fosse físico — só que na nuvem.

**Características:**
- Controle total sobre o ambiente (SO, runtime, dependências)
- Diversos tipos de instância (geral, otimizada para CPU, memória, GPU)
- Escalonamento manual ou via Auto Scaling Groups
- Cobrança por tempo de uso (on-demand, reserved, spot)

### Vantagens
- Flexibilidade máxima — qualquer stack, qualquer configuração
- Ideal para cargas previsíveis e de longa duração
- Spot Instances reduzem custo drasticamente para workloads tolerantes a interrupção
- Acesso root permite otimizações finas de performance

### Desvantagens
- Responsabilidade total por patches, segurança e manutenção do SO
- Escalonamento não é instantâneo (minutos, não segundos)
- Custo "ocioso" se a instância roda 24/7 sem uso constante
- Exige conhecimento de infraestrutura (rede, segurança, monitoramento manual)

**Quando usar:** aplicações com requisitos específicos de SO/runtime, workloads de longa duração e previsíveis, ou quando você precisa de controle total sobre o ambiente.

---

## ECS (Elastic Container Service)

Orquestração de containers Docker, com duas formas de execução: **EC2** (você gerencia os hosts) ou **Fargate** (serverless, AWS gerencia a infraestrutura).

**Características:**
- Define serviços e tasks via Task Definitions (JSON)
- Integração nativa com ALB, IAM, CloudWatch e Service Discovery
- Suporta scaling automático baseado em métricas

### Vantagens
- Abstrai a complexidade de orquestração (comparado a gerenciar Kubernetes)
- Modo Fargate elimina gerenciamento de servidores
- Boa integração com o ecossistema AWS (sem necessidade de ferramentas externas)
- Deploys mais rápidos que EC2 puro, com rollback simplificado

### Desvantagens
- Menos portável que Kubernetes (vendor lock-in mais forte)
- Fargate tem custo por vCPU/memória mais alto que EC2 equivalente
- Curva de aprendizado para Task Definitions, Service Discovery e networking (ENI por task)
- Funcionalidades avançadas de orquestração são mais limitadas que EKS/Kubernetes

**Quando usar:** aplicações containerizadas que precisam de orquestração sem a complexidade operacional do Kubernetes.

---

## Lambda

Computação serverless orientada a eventos. Você sobe uma função, define o gatilho (API Gateway, S3, SQS, EventBridge etc.) e a AWS executa e escala automaticamente.

**Características:**
- Sem servidor para gerenciar
- Cobrança por execução (tempo + memória usada)
- Timeout máximo de 15 minutos
- Escala automaticamente de zero a milhares de execuções concorrentes

### Vantagens
- Custo zero quando não há execução (ideal para cargas esporádicas)
- Escalonamento automático e instantâneo
- Reduz drasticamente a responsabilidade operacional (sem patch, sem SO)
- Integração nativa com praticamente todo o ecossistema de eventos da AWS

### Desvantagens
- **Cold start** — latência adicional na primeira invocação após período ocioso
- Timeout de 15 minutos inviabiliza processamentos longos
- Debugging e observabilidade local são mais difíceis que em servidores tradicionais
- Custo pode escalar mal em cargas constantes e de alto volume (EC2/ECS ficam mais baratos)
- Limites de memória, payload e camadas (layers) exigem atenção arquitetural

**Quando usar:** processamento orientado a eventos, cargas esporádicas ou imprevisíveis, integrações leves (webhooks, transformação de dados, glue code).

---

## SNS (Simple Notification Service)

Serviço de **pub/sub** (publish/subscribe). Um produtor publica uma mensagem em um tópico, e todos os assinantes (SQS, Lambda, e-mail, SMS, HTTP) recebem uma cópia.

**Características:**
- Modelo fan-out: uma mensagem, múltiplos destinos
- Suporta filtros por atributo de mensagem
- Entrega "at-least-once"

### Vantagens
- Simplifica arquiteturas de broadcast (vários consumidores reagem ao mesmo evento)
- Baixa latência de entrega
- Integração simples com SQS, Lambda e endpoints HTTP/e-mail/SMS
- Reduz acoplamento entre serviços produtores e consumidores

### Desvantagens
- Sem garantia de ordenação (exceto em tópicos FIFO, com limitações de throughput)
- Sem retenção de mensagem — se o assinante estiver indisponível, a entrega pode falhar (mitigado por SNS + SQS)
- Não foi pensado para fila de processamento, apenas notificação/distribuição
- Debugging de mensagens "perdidas" é mais difícil sem configuração extra (dead-letter queue)

**Quando usar:** notificar múltiplos serviços sobre um evento (ex: "pedido criado" notificando estoque, faturamento e e-mail simultaneamente).

---

## SQS (Simple Queue Service)

Fila de mensagens. Um produtor envia, um (ou mais) consumidor processa e remove da fila. Modelo **ponto a ponto**, diferente do fan-out do SNS.

**Características:**
- Filas Standard (alta taxa, ordenação não garantida) ou FIFO (ordenação garantida, menor throughput)
- Suporta Dead Letter Queue (DLQ) para mensagens com falha repetida
- Visibility timeout evita processamento duplicado durante o tempo de processamento

### Vantagens
- Desacopla produtor e consumidor — consumidor processa no próprio ritmo
- Resiliente a picos de tráfego (a fila absorve o excesso)
- DLQ facilita tratamento de mensagens com erro recorrente
- Baixo custo e gerenciamento praticamente zero

### Desvantagens
- Filas Standard não garantem ordem nem exatamente-uma-entrega (podem duplicar)
- Filas FIFO têm throughput limitado comparado às Standard
- Não é pub/sub nativo — para múltiplos consumidores do mesmo evento, precisa combinar com SNS
- Mensagens têm tamanho máximo (256 KB), exigindo referência externa (S3) para payloads grandes

**Quando usar:** desacoplar processamento assíncrono (ex: processar pagamentos, gerar relatórios, enviar e-mails) sem que o produtor espere a conclusão.

> **Padrão comum:** SNS + SQS ("fan-out"). O produtor publica uma vez no SNS, que distribui para várias filas SQS — cada serviço consome no seu próprio ritmo, sem acoplamento e sem perda de mensagens.

---

## RDS (Relational Database Service)

Banco relacional gerenciado (PostgreSQL, MySQL, MariaDB, Oracle, SQL Server). A AWS cuida de backups, patches, replicação e failover.

**Características:**
- Multi-AZ para alta disponibilidade
- Read Replicas para escalar leitura
- Backups automáticos e point-in-time recovery

### Vantagens
- Elimina trabalho operacional de manutenção de banco (patch, backup, failover)
- Suporte nativo a transações ACID e SQL completo
- Multi-AZ garante failover automático em caso de falha
- Read Replicas aliviam carga de leitura sem afetar o banco primário

### Desvantagens
- Escalonamento vertical tem limite (instância maior = mais caro, não infinito)
- Menos flexível que self-managed para configurações muito específicas
- Failover Multi-AZ não é instantâneo (segundos de indisponibilidade)
- Custo cresce rápido com Multi-AZ + Read Replicas + storage de alta performance

**Quando usar:** dados transacionais com forte necessidade de integridade referencial e ACID — pagamentos, contas, pedidos.

---

## DynamoDB

Banco NoSQL chave-valor / documento, totalmente gerenciado e serverless. Escala horizontalmente de forma nativa.

**Características:**
- Modelo de dados baseado em partition key (+ sort key opcional)
- Capacidade provisionada ou on-demand (paga por uso)
- DynamoDB Streams para capturar mudanças em tempo real

### Vantagens
- Latência de milissegundos consistente, independente do volume de dados
- Escala horizontalmente sem intervenção manual
- Sem servidor para gerenciar — totalmente serverless
- Integração nativa com Lambda (via Streams) para arquiteturas event-driven

### Desvantagens
- Modelagem exige planejamento dos padrões de acesso desde o início (difícil mudar depois)
- Sem JOINs nativos — relacionamentos exigem denormalização ou múltiplas queries
- Consultas ad-hoc (fora dos padrões de acesso modelados) são limitadas ou caras
- Custo pode surpreender em modo on-demand com alto volume de leitura/escrita

**Quando usar:** alta escala de leitura/escrita com padrões de acesso bem definidos — catálogos, sessões, contadores, dados de IoT.

---

## SageMaker

Plataforma completa para construir, treinar e implantar modelos de machine learning.

**Características:**
- Notebooks gerenciados para experimentação
- Treinamento distribuído com infraestrutura provisionada automaticamente
- Endpoints gerenciados para inferência em tempo real ou batch

### Vantagens
- Cobre todo o ciclo de vida de ML (dados → treino → deploy → monitoramento) em um só serviço
- Infraestrutura de treino sob demanda (não precisa manter GPUs ociosas)
- Integração com S3, IAM e outros serviços AWS
- Suporta modelos próprios e modelos pré-treinados (JumpStart, Bedrock para LLMs)

### Desvantagens
- Custo elevado, especialmente em treino com GPU e endpoints sempre ativos
- Curva de aprendizado alta — exige conhecimento de ML e de infraestrutura AWS
- Vendor lock-in forte (migrar pipelines para outro provedor exige retrabalho)
- Para times pequenos ou modelos simples, pode ser overengineering

**Quando usar:** times que precisam de pipeline completo de ML em produção, com necessidade de escalar treino e inferência.

---

## CloudWatch

Serviço de observabilidade: métricas, logs e alarmes para os demais serviços AWS (e aplicações próprias via SDK).

**Características:**
- Métricas automáticas para EC2, ECS, Lambda, RDS, etc.
- CloudWatch Logs para centralizar logs de aplicação
- Alarms para disparar ações (SNS, Auto Scaling) baseadas em thresholds

### Vantagens
- Integração nativa e automática com todos os serviços AWS (zero configuração inicial)
- Alarms conectam diretamente a ações automatizadas (scaling, notificação)
- Dashboards centralizados para métricas de toda a infraestrutura
- Logs Insights permite queries ad-hoc sobre logs sem ferramenta externa

### Desvantagens
- Interface e query language (Logs Insights) menos amigáveis que ferramentas especializadas (Datadog, Grafana)
- Custo cresce rápido com alto volume de logs e métricas customizadas
- Retenção de logs exige configuração explícita (padrão pode ser insuficiente)
- Correlação entre métricas, logs e traces é mais limitada que em plataformas de observabilidade dedicadas (APM completo)

**Quando usar:** observabilidade básica a intermediária dentro do ecossistema AWS, ou como complemento a uma ferramenta de APM dedicada.

---

## Comparativo Geral

| Serviço     | Categoria        | Gerenciamento         | Escalonamento        | Melhor para                                  |
|-------------|-------------------|------------------------|------------------------|-----------------------------------------------|
| **EC2**     | Computação        | Você gerencia o SO     | Manual / Auto Scaling  | Controle total, cargas previsíveis            |
| **ECS**     | Computação        | Parcial (Fargate=zero) | Automático             | Containers sem complexidade de Kubernetes     |
| **Lambda**  | Computação        | Zero (serverless)      | Automático e instantâneo | Eventos esporádicos, processamento curto    |
| **SNS**     | Mensageria        | Zero (serverless)      | Automático             | Notificação fan-out (1 → N consumidores)      |
| **SQS**     | Mensageria        | Zero (serverless)      | Automático             | Fila ponto a ponto, desacoplamento assíncrono |
| **RDS**     | Dados (SQL)       | Parcial (AWS gerencia infra) | Vertical + réplicas | Dados relacionais, ACID, integridade forte |
| **DynamoDB**| Dados (NoSQL)     | Zero (serverless)      | Horizontal nativo      | Alta escala, baixa latência, acesso previsível|
| **SageMaker**| Machine Learning | Parcial                | Automático sob demanda | Ciclo completo de ML em produção             |
| **CloudWatch**| Observabilidade | Zero (serverless)      | Automático             | Métricas, logs e alarmes nativos da AWS       |

### Computação: EC2 vs ECS vs Lambda

| Critério            | EC2                  | ECS (Fargate)         | Lambda                  |
|---------------------|------------------------|--------------------------|---------------------------|
| Controle do ambiente| Total                  | Alto (container)         | Baixo (runtime gerenciado)|
| Gerenciamento de SO | Manual                 | Não (Fargate)            | Não                        |
| Tempo de start       | Minutos (boot)         | Segundos                 | Milissegundos a segundos (cold start) |
| Custo em carga constante | Mais barato        | Intermediário             | Pode ficar caro            |
| Custo em carga esporádica | Caro (ocioso)     | Intermediário              | Mais barato                |
| Limite de execução   | Sem limite             | Sem limite                | 15 minutos por invocação   |

### Mensageria: SNS vs SQS

| Critério         | SNS (pub/sub)                  | SQS (fila)                        |
|-------------------|----------------------------------|--------------------------------------|
| Modelo            | Fan-out (1 produtor → N consumidores) | Ponto a ponto (1 mensagem → 1 consumidor) |
| Retenção           | Não retém (entrega imediata)    | Retém até ser processada (até 14 dias)|
| Caso de uso típico | Broadcast de eventos             | Fila de trabalho assíncrono           |
| Combinação comum  | SNS → várias filas SQS (fan-out resiliente) | Consome de SNS ou direto de produtores |

### Dados: RDS vs DynamoDB

| Critério              | RDS (relacional)             | DynamoDB (NoSQL)              |
|------------------------|---------------------------------|----------------------------------|
| Modelo de dados         | Tabelas com esquema fixo      | Chave-valor / documento flexível |
| Transações ACID         | Suporte completo                | Suporte parcial (transações limitadas) |
| JOINs                   | Nativos                         | Inexistentes (denormalização)    |
| Escalonamento            | Vertical + réplicas de leitura | Horizontal nativo                 |
| Previsibilidade de latência | Variável com carga          | Consistente em qualquer escala    |
| Caso de uso típico       | Pagamentos, contas, pedidos    | Catálogos de alta escala, sessões, IoT |

> **Contexto real:** uma arquitetura típica de e-commerce combina RDS para pedidos e pagamentos (integridade crítica), DynamoDB para catálogo de produtos (alta escala de leitura), SQS para processar pedidos de forma assíncrona, SNS para notificar múltiplos serviços sobre eventos, Lambda para tarefas leves orientadas a evento, ECS/EC2 para a API principal e CloudWatch para observabilidade de tudo.

---

## Referências

- [AWS Documentation](https://docs.aws.amazon.com/) — referência oficial
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/) — boas práticas de arquitetura
- [AWS Pricing Calculator](https://calculator.aws/) — estimativa de custos

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
