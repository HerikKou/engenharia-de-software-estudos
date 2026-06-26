# Backend Study Hub

Repositório de estudos contínuos sobre desenvolvimento backend, arquitetura de software e o ecossistema Java.

## Sobre este repositório

Este hub centraliza minha jornada de aprendizado em backend — não apenas como registro teórico, mas como um espaço onde conceitos se tornam aplicáveis em cenários reais de sistemas. O conteúdo é atualizado continuamente conforme avanço nos estudos e projetos práticos.

## Módulos

| #  | Tema                          | Conteúdo                                                  |
|----|-------------------------------|------------------------------------------------------------|
| 01 | Fundamentos                   | Tipos primitivos, estruturas de dados (List, Set, Map), OOP |
| 02 | Banco De Dados                | Relacional vs NoSQL, SQL, modelagem, integridade            |
| 03 | Arquitetura                   | Monólito vs Microserviços, Clean Architecture, API Gateway  |
| 04 | Performance e Escalabilidade  | Cache, otimização de queries, Redis                         |
| 05 | Sistemas Distribuídos         | Kafka, RabbitMQ, event-driven architecture                  |
| 06 | Segurança                     | Autenticação, autorização, JWT, OAuth2                       |
| 07 | Observabilidade               | Logs estruturados, métricas, monitoramento                  |
| 08 | Cloud                         | AWS — EC2, ECS, Lambda, SNS, SQS, RDS, DynamoDB, SageMaker, CloudWatch |
| 09 | Infraestrutura                | Docker, Docker Compose, variáveis de ambiente                |

## Projetos Práticos

Os conceitos deste hub são aplicados diretamente nos projetos do portfólio, organizados por evolução arquitetural:

| Projeto                                                                                    | Descrição                                                                                                          | Conceitos Aplicados                                                                                       |
|-----------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| [Processamento_Bancario](https://github.com/HerikKou/Processamento_Bancario)                  | Gateway de pagamentos com 4 microsserviços (Cliente, Conta, Transação, Pagamento) + API Gateway, autenticação JWT      | Spring Cloud Gateway, Kafka, Spring Security/JWT, banco por serviço, MySQL                                       |
| [ProjetoAntifraude](https://github.com/HerikKou/ProjetoAntifraude)                              | Processamento de pagamentos com detecção de fraude em tempo real via IA (scikit-learn) e observabilidade com Datadog   | Kafka, FastAPI, scikit-learn (ML), Datadog (observabilidade), MySQL, testes com Pytest                            |
| [Controle_Financeiro](https://github.com/HerikKou/Controle_financeiro)                         | Plataforma de controle financeiro: registra pagamentos, consolida gastos mensais e gera insights com Claude (Anthropic) | Arquitetura orientada a eventos (Kafka), banco por serviço, IA generativa (Claude), PostgreSQL, deploy em ECS/AWS |
| [Limites-de-credito-com-IA](https://github.com/HerikKou/Limites-de-credito-com-IA)             | Análise de limite de crédito com 5 microsserviços: histórico, score de risco, explicação via LLM e notificação        | Kafka (multi-consumer), cálculo de score, Claude API, idempotência, retry/DLQ, cache em memória                   |
| [Plataforma_de_Gestao_de_Boletos](https://github.com/HerikKou/Plataforma_de_Gestao_de_Boletos) | Gestão de boletos bancários com vencimento automático, juros e pagamento, modelado como State Machine                  | Microsserviços orientados a eventos, State Machine, idempotência, retry + Dead Letter Topic, API Gateway, Angular |

## Projetos para Resolver Problemas Reais

Ideias de sistemas voltados a problemas do dia a dia, que servem tanto como portfólio quanto como uso pessoal:

| Projeto sugerido                          | Problema real que resolve                                      | Módulos envolvidos                          |
|---------------------------------------------|-------------------------------------------------------------------|------------------------------------------------|
| Controle Financeiro Pessoal                 | Organizar gastos, receitas e metas de economia mensais            | Banco de Dados, Arquitetura                   |
| Divisão de Contas em Grupo                  | Calcular quem deve quanto entre amigos/colegas de casa             | Banco de Dados, Fundamentos                   |
| Alerta de Gastos Anormais                   | Notificar quando um gasto sai do padrão histórico do usuário      | Sistemas Distribuídos, Cloud (SageMaker)      |
| Conciliação Bancária Automática             | Cruzar extrato do banco com lançamentos internos e achar divergências | Banco de Dados, Performance                |
| Planejador de Orçamento com Categorização   | Classificar despesas automaticamente por categoria (IA)           | Cloud (SageMaker/LLM), Banco de Dados         |
| Controle de Assinaturas Recorrentes         | Listar e alertar sobre assinaturas ativas antes da renovação       | Banco de Dados, Observabilidade               |
| Gestão de Estoque para Pequeno Comércio     | Evitar ruptura ou excesso de estoque, alertar reposição            | Banco de Dados, Sistemas Distribuídos         |
| Simulador de Score de Crédito               | Avaliar risco de inadimplência com base em histórico do cliente   | Cloud (SageMaker), Segurança                  |
| Detector de Cobranças Duplicadas            | Identificar transações duplicadas em faturas de cartão             | Banco de Dados, Performance                   |
| Sistema de Reembolso Corporativo            | Fluxo de aprovação de despesas de viagem/equipe                    | Arquitetura, Segurança                        |
| Monitor de Metas Financeiras                | Acompanhar progresso de metas (ex: reserva de emergência)          | Banco de Dados, Observabilidade               |

> Esses projetos têm valor extra: além de praticar os conceitos técnicos, resolvem problemas que você (ou qualquer pessoa) provavelmente já enfrentou — o que ajuda a justificar decisões de produto e arquitetura em entrevistas e no portfólio.

## Ideias de Projetos para Praticar

Sugestões de projetos para aplicar os conceitos de cada módulo, do mais simples ao mais avançado:

| Projeto sugerido                     | Módulos envolvidos                          | Conceitos praticados                                              |
|----------------------------------------|----------------------------------------------|----------------------------------------------------------------------|
| API de Gerenciamento de Tarefas        | Fundamentos, Banco de Dados                  | CRUD, modelagem relacional, constraints, validação                   |
| Encurtador de URLs                     | Banco de Dados, Performance                  | Índices, cache com Redis, geração de chaves únicas                   |
| Sistema de Pedidos com Estoque         | Banco de Dados, Arquitetura                  | Transações ACID, normalização, Clean Architecture                    |
| Notificador de Eventos                 | Sistemas Distribuídos, Cloud                 | SNS + SQS (fan-out), consumidores assíncronos                        |
| Processador de Pagamentos Assíncrono   | Sistemas Distribuídos, Cloud, Segurança      | Kafka ou SQS, idempotência, JWT para autenticação dos endpoints      |
| Dashboard de Métricas em Tempo Real    | Observabilidade, Performance                 | Logs estruturados, métricas customizadas, cache de agregações        |
| API com Autenticação e Autorização     | Segurança, Arquitetura                       | JWT, OAuth2, RBAC, API Gateway                                       |
| Catálogo de Produtos de Alta Escala    | Banco de Dados, Cloud                        | DynamoDB, modelagem por padrão de acesso, escalonamento horizontal   |
| Pipeline de Detecção de Fraude         | Sistemas Distribuídos, Cloud, Observabilidade| Kafka, SageMaker, métricas de modelo, monitoramento                  |
| Sistema de Reconciliação de Lote       | Banco de Dados, Performance                  | Processamento em lote, consistência eventual, otimização de queries  |
| Deploy Completo em Containers          | Infraestrutura, Cloud                        | Docker Compose, ECS/Fargate, variáveis de ambiente, CI/CD básico      |

> A ideia é evoluir os projetos em complexidade: comece com fundamentos e banco de dados, depois introduza mensageria e arquitetura distribuída, e só então avance para observabilidade, segurança e cloud em produção.

## Stack Principal

```
Linguagem:        Java 21
Framework:        Spring Boot 3
Mensageria:       Apache Kafka
Banco:            PostgreSQL
Cache:            Redis
Containers:       Docker + Docker Compose
Observabilidade:  Datadog APM
Cloud:            AWS
IA:               Claude API (Anthropic)
```

## Autor

**Herik Kato** — desenvolvedor backend em evolução contínua.

[GitHub](https://github.com/HerikKou) · [LinkedIn](https://www.linkedin.com/in/herikkato/)

> "Documentar é parte do aprendizado."
