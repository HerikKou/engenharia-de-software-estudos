# 02 · Banco de Dados

Toda aplicação backend relevante depende de persistência. Este módulo cobre os fundamentos de bancos relacionais e não relacionais, modelagem de dados, SQL e os mecanismos que garantem integridade e consistência — habilidades essenciais para qualquer engenheiro backend.

---

## Banco Relacional vs Não Relacional

A escolha entre SQL e NoSQL não é uma questão de modismo — é uma decisão arquitetural baseada nas características do seu domínio.

### Banco Relacional (SQL)

Dados organizados em tabelas com esquema fixo. Relacionamentos são estabelecidos por chaves estrangeiras. Suporta transações ACID.

**Exemplos:** PostgreSQL, MySQL, Oracle, SQL Server

**Características:**
- Esquema bem definido (schema-on-write)
- Suporte nativo a JOINs
- Transações com garantias ACID
- Ideal para dados com forte relacionamento entre entidades
- Consultas ad-hoc flexíveis com SQL

**Quando usar:**
- Dados financeiros (transações, contas, pagamentos)
- Sistemas com integridade referencial crítica
- Relatórios e análises que cruzam múltiplas entidades
- Aplicações onde consistência é prioridade sobre velocidade de escrita

---

### Banco Não Relacional (NoSQL)

Dados armazenados de formas variadas: documentos, chave-valor, grafos, colunas. Esquema flexível. Escala horizontal com maior facilidade.

**Exemplos:** MongoDB (documentos), Redis (chave-valor), Cassandra (colunas largas), Neo4j (grafos)

**Características:**
- Esquema flexível (schema-on-read)
- Alta disponibilidade e escalabilidade horizontal
- Performance superior para leitura/escrita de alta frequência
- Sem suporte nativo a JOINs complexos

**Quando usar:**
- Cache e sessões (Redis)
- Catálogos de produtos com estrutura variável
- Logs e eventos em alto volume
- Dados não estruturados ou semiestruturados

---

### Comparativo

| Critério              | Relacional (SQL)            | Não Relacional (NoSQL)         |
|-----------------------|-----------------------------|--------------------------------|
| Estrutura             | Tabelas e colunas           | Documentos, chave-valor, etc.  |
| Esquema               | Rígido, definido antes      | Flexível, evolui com os dados  |
| Relacionamentos       | JOINs nativos               | Denormalização ou referências  |
| Transações ACID       | Suporte completo            | Variável por implementação     |
| Escalabilidade        | Vertical (principalmente)   | Horizontal (nativo)            |
| Consistência          | Forte por padrão            | Eventual (BASE model)          |
| Consultas complexas   | SQL flexível                | Limitado ao modelo do banco    |

> **Contexto real:** em microserviços financeiros, o padrão comum é PostgreSQL por serviço para dados transacionais + Redis para cache de sessão/rate limiting. Cada serviço tem seu próprio banco — evita acoplamento entre domínios.

---

## SQL — Básico e Intermediário

### Consultas Básicas

```sql
-- Seleção com filtro
SELECT id, valor, status, criada_em
FROM transacoes
WHERE status = 'PENDENTE'
  AND criada_em >= NOW() - INTERVAL '24 hours';

-- Ordenação e limite
SELECT *
FROM transacoes
ORDER BY criada_em DESC
LIMIT 50;

-- Contagem e agrupamento
SELECT status, COUNT(*) AS total, SUM(valor) AS volume
FROM transacoes
GROUP BY status;
```

---

### JOINs

```sql
-- INNER JOIN: apenas registros com correspondência nos dois lados
SELECT t.id, t.valor, c.nome AS cliente
FROM transacoes t
INNER JOIN clientes c ON c.id = t.cliente_id
WHERE t.status = 'APROVADA';

-- LEFT JOIN: todos os registros da tabela à esquerda,
--            com ou sem correspondência à direita
SELECT c.id, c.nome, COUNT(t.id) AS total_transacoes
FROM clientes c
LEFT JOIN transacoes t ON t.cliente_id = c.id
GROUP BY c.id, c.nome;
```

---

### Agregações

```sql
-- Volume diário de transações
SELECT
    DATE(criada_em) AS data,
    COUNT(*) AS quantidade,
    SUM(valor) AS volume_total,
    AVG(valor) AS ticket_medio,
    MAX(valor) AS maior_transacao
FROM transacoes
WHERE criada_em >= NOW() - INTERVAL '30 days'
GROUP BY DATE(criada_em)
ORDER BY data DESC;

-- HAVING: filtro sobre resultado agregado
SELECT cliente_id, COUNT(*) AS total
FROM transacoes
GROUP BY cliente_id
HAVING COUNT(*) > 10;
```

---

### Subqueries e CTEs

```sql
-- CTE (Common Table Expression) — mais legível que subquery aninhada
WITH transacoes_suspeitas AS (
    SELECT cliente_id
    FROM transacoes
    WHERE valor > 10000
      AND criada_em >= NOW() - INTERVAL '1 hour'
    GROUP BY cliente_id
    HAVING COUNT(*) > 5
)
SELECT c.nome, c.email
FROM clientes c
INNER JOIN transacoes_suspeitas ts ON ts.cliente_id = c.id;
```

---

## Modelagem de Dados

### Entidades e Relacionamentos

Uma boa modelagem começa pela identificação das entidades do domínio e seus relacionamentos.

**Tipos de relacionamento:**
- **1:1** — Um cliente tem um endereço principal
- **1:N** — Um cliente tem muitas transações
- **N:N** — Um pedido contém muitos produtos, um produto aparece em muitos pedidos

```sql
-- Entidade principal
CREATE TABLE clientes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        VARCHAR(255) NOT NULL,
    documento   VARCHAR(14) NOT NULL UNIQUE,
    criado_em   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Entidade dependente (1:N com clientes)
CREATE TABLE transacoes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id  UUID NOT NULL REFERENCES clientes(id),
    valor       NUMERIC(15, 2) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    criada_em   TIMESTAMP NOT NULL DEFAULT NOW()
);
```

---

### Normalização

A normalização reduz redundância e previne anomalias de atualização.

**1FN (Primeira Forma Normal):** cada coluna contém um único valor atômico. Sem grupos repetidos.

```sql
-- ❌ Violação de 1FN: múltiplos telefones em uma coluna
-- cliente_id | nome  | telefones
-- 1          | Herik | "11-9999, 11-8888"

-- ✅ Correto: tabela separada para telefones
CREATE TABLE telefones (
    id         SERIAL PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    numero     VARCHAR(20) NOT NULL
);
```

**2FN:** atende 1FN + todos os atributos não-chave dependem totalmente da chave primária.

**3FN:** atende 2FN + sem dependências transitivas (atributos não-chave não dependem de outros atributos não-chave).

> Para a maioria dos sistemas, a 3FN é suficiente. Desnormalização controlada é aceitável em casos de performance crítica, com justificativa documentada.

---

## Integridade e Consistência

### Constraints

Constraints são regras definidas na camada do banco — a última linha de defesa contra dados inválidos.

```sql
CREATE TABLE pagamentos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transacao_id    UUID NOT NULL REFERENCES transacoes(id) ON DELETE RESTRICT,
    valor           NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    metodo          VARCHAR(20) NOT NULL CHECK (metodo IN ('PIX', 'TED', 'DOC')),
    idempotency_key VARCHAR(64) UNIQUE,
    processado_em   TIMESTAMP
);
```

**Tipos de constraint:**

| Constraint      | Função                                                   |
|-----------------|----------------------------------------------------------|
| `PRIMARY KEY`   | Identificador único, implica `NOT NULL` + `UNIQUE`       |
| `FOREIGN KEY`   | Garante integridade referencial entre tabelas            |
| `UNIQUE`        | Impede valores duplicados na coluna                      |
| `NOT NULL`      | Coluna obrigatória                                       |
| `CHECK`         | Valida condição booleana antes de inserir/atualizar      |
| `DEFAULT`       | Valor padrão quando não informado                        |

---

### Índices

Índices aceleram consultas ao custo de maior uso de disco e escrita mais lenta.

```sql
-- Índice simples
CREATE INDEX idx_transacoes_cliente ON transacoes(cliente_id);

-- Índice composto (útil quando filtra por múltiplas colunas juntas)
CREATE INDEX idx_transacoes_status_data ON transacoes(status, criada_em DESC);

-- Índice único (equivale a constraint UNIQUE + índice)
CREATE UNIQUE INDEX idx_pagamentos_idempotency ON pagamentos(idempotency_key);
```

**Regra geral:** crie índices nas colunas que aparecem frequentemente em `WHERE`, `JOIN ON` e `ORDER BY`. Evite índices em colunas com baixa cardinalidade (como `boolean`).

---

### Transações e ACID

```sql
BEGIN;

UPDATE contas SET saldo = saldo - 100 WHERE id = 'conta-origem';
UPDATE contas SET saldo = saldo + 100 WHERE id = 'conta-destino';

-- Se qualquer UPDATE falhar, ROLLBACK desfaz tudo
COMMIT;
```

**Propriedades ACID:**

| Propriedade   | Significado                                                          |
|---------------|----------------------------------------------------------------------|
| **Atomicidade** | Tudo ou nada — todas as operações são confirmadas ou nenhuma é     |
| **Consistência** | O banco sempre passa de um estado válido para outro estado válido |
| **Isolamento** | Transações concorrentes não interferem entre si                     |
| **Durabilidade** | Após o COMMIT, os dados persistem mesmo em caso de falha          |

---

## Referências

- [PostgreSQL Documentation](https://www.postgresql.org/docs/) — referência oficial
- *Database Design for Mere Mortals* — Michael Hernandez
- [Use The Index, Luke](https://use-the-index-luke.com/) — guia de performance com índices

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
