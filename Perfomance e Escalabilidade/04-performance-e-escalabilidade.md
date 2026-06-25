# 04 · Performance e Escalabilidade

Sistema que funciona é requisito mínimo. Sistema que funciona sob carga é onde mora a engenharia real. Este módulo cobre as técnicas fundamentais de otimização — cache, consultas eficientes e Redis — que aparecem em praticamente todo sistema backend com requisitos de performance.

---

## Cache

Cache é uma camada de armazenamento temporário que guarda resultados de operações custosas para evitar recalculá-las.

### Por que usar cache?

| Operação               | Latência típica      |
|------------------------|----------------------|
| Acesso à memória (L1)  | ~1 ns                |
| Acesso à memória RAM   | ~100 ns              |
| SSD (NVMe)             | ~100 µs              |
| Banco de dados (query) | ~1–10 ms             |
| Rede (mesmo datacenter)| ~1 ms                |
| API externa            | ~100–500 ms          |

Cache elimina operações de alta latência substituindo-as por leitura em memória.

---

### Estratégias de Leitura

**Cache-Aside (Lazy Loading)**

O padrão mais comum. A aplicação controla o cache diretamente.

```
1. Aplicação verifica o cache
2. Se HIT → retorna dado do cache
3. Se MISS → busca no banco, armazena no cache, retorna
```

**Vantagens:** simples de implementar; só carrega o que é realmente solicitado; falha no cache não derruba a aplicação (fallback para o banco).  
**Desvantagens:** primeiro acesso sempre vai ao banco (cache miss); risco de thundering herd (muitas requisições simultâneas no mesmo MISS); cache pode ficar desatualizado entre escritas.

```java
public ContaDTO buscarConta(String contaId) {
    String cacheKey = "conta:" + contaId;

    // 1. Tenta o cache
    ContaDTO cached = cache.get(cacheKey, ContaDTO.class);
    if (cached != null) {
        return cached;
    }

    // 2. MISS: busca no banco
    ContaDTO conta = repository.findById(contaId)
        .map(ContaDTO::from)
        .orElseThrow(() -> new ContaNaoEncontradaException(contaId));

    // 3. Armazena no cache com TTL
    cache.put(cacheKey, conta, Duration.ofMinutes(10));

    return conta;
}
```

**Read-Through**

O cache intercepta a leitura automaticamente. A aplicação sempre lê do cache, e este busca no banco quando necessário. Transparente para o código de negócio.

**Vantagens:** código da aplicação não precisa gerenciar o cache diretamente; consistência garantida pelo cache provider.  
**Desvantagens:** dependência do cache provider suportar este padrão; primeiro acesso ainda vai ao banco.

**Write-Through**

Escrita vai simultaneamente para o cache e para o banco. Garante consistência, mas adiciona latência na escrita.

**Vantagens:** cache sempre atualizado após cada escrita; sem risco de dado desatualizado.  
**Desvantagens:** latência de escrita aumenta (duas operações síncronas); dados raramente lidos também ocupam o cache desnecessariamente.

**Write-Behind (Write-Back)**

Escrita vai para o cache imediatamente, o banco é atualizado de forma assíncrona. Maior throughput de escrita, risco de perda de dados em falhas.

**Vantagens:** escrita extremamente rápida (apenas no cache); absorve picos de escrita sem pressionar o banco.  
**Desvantagens:** risco de perda de dados se o cache falhar antes de persistir; complexidade de implementação maior; não indicado para dados financeiros críticos.

---

### Invalidação de Cache

> *"Existem apenas dois problemas difíceis em ciência da computação: invalidação de cache e nomear coisas."* — Phil Karlton

**Estratégias de invalidação:**

**TTL (Time-To-Live):** dado expira automaticamente após um período. Simples, mas aceita dados levemente desatualizados.

```java
// dado expira em 5 minutos — adequado para saldo de conta
cache.put("saldo:" + contaId, saldo, Duration.ofMinutes(5));
```

**Invalidação explícita:** quando o dado é atualizado, o cache é limpo.

```java
@Transactional
public void atualizarSaldo(String contaId, BigDecimal novoSaldo) {
    repository.atualizarSaldo(contaId, novoSaldo);
    cache.delete("saldo:" + contaId); // invalida após escrita
}
```

**Cache-aside com versionamento:** inclui versão na chave do cache.

```java
String cacheKey = "conta:" + contaId + ":v" + conta.getVersao();
```

**Boas práticas:**
- Sempre defina TTL — cache sem expiração vira memória perdida
- Prefira chaves descritivas: `"transacao:{id}"`, `"score:cliente:{id}"`
- Em dados financeiros, TTL curto ou invalidação explícita são mandatórios
- Monitore hit rate (razão entre hits e total de acessos) — abaixo de 80% indica design ruim

---

## Otimização de Consultas

### Índices

O índice mais comum em bancos relacionais é o **B-Tree**, que organiza os dados de forma que buscas, ranges e ordenações são eficientes.

```sql
-- Sem índice: full table scan (O(n))
SELECT * FROM transacoes WHERE cliente_id = 'abc123';

-- Com índice: index scan (O(log n))
CREATE INDEX idx_transacoes_cliente ON transacoes(cliente_id);
```

**Índice composto:** útil quando queries filtram por múltiplas colunas juntas.

```sql
-- Query que se beneficia de índice composto
SELECT * FROM transacoes
WHERE cliente_id = 'abc123'
  AND status = 'PENDENTE'
ORDER BY criada_em DESC;

-- Índice composto na ordem certa (igualdade → range → ordem)
CREATE INDEX idx_transacoes_cliente_status_data
    ON transacoes(cliente_id, status, criada_em DESC);
```

**Índice parcial:** indexa apenas um subconjunto de linhas.

```sql
-- Apenas transações pendentes — muito menor que o índice completo
CREATE INDEX idx_transacoes_pendentes
    ON transacoes(cliente_id)
    WHERE status = 'PENDENTE';
```

**Quando NÃO criar índice:**
- Colunas com baixa cardinalidade (poucos valores distintos, como `boolean`)
- Tabelas muito pequenas (full scan é mais rápido)
- Colunas que raramente aparecem em filtros

---

### Lazy Loading vs Eager Loading

Em JPA/Hibernate, o modo de carregamento define quando relacionamentos são buscados no banco.

**Eager Loading:** busca o relacionamento junto com a entidade principal, sempre.

```java
@OneToMany(fetch = FetchType.EAGER) // carrega transações junto com o cliente
private List<Transacao> transacoes;
```

**Risco:** N+1 queries acidentais. Ao buscar 100 clientes, você pode disparar 101 queries.

**Lazy Loading:** o relacionamento só é carregado quando acessado no código.

```java
@OneToMany(fetch = FetchType.LAZY) // padrão para coleções — carrega sob demanda
private List<Transacao> transacoes;
```

**Solução para N+1 com Lazy:** JOIN FETCH nas queries que precisam dos dados.

```java
@Query("SELECT c FROM Cliente c JOIN FETCH c.transacoes WHERE c.id = :id")
Optional<Cliente> findByIdComTransacoes(@Param("id") UUID id);
```

---

### Paginação

Nunca retorne todos os registros de uma tabela em uma única query em produção.

```java
// Spring Data JPA com Pageable
public Page<TransacaoDTO> listar(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("criadaEm").descending());
    return repository.findAll(pageable).map(TransacaoDTO::from);
}
```

```sql
-- SQL equivalente
SELECT * FROM transacoes
ORDER BY criada_em DESC
LIMIT 20 OFFSET 40; -- página 3, 20 itens por página
```

**Cursor-based pagination:** alternativa mais performática para grandes volumes.

```sql
-- Mais eficiente que OFFSET em tabelas grandes
SELECT * FROM transacoes
WHERE criada_em < '2024-01-15 10:00:00' -- cursor da última página
ORDER BY criada_em DESC
LIMIT 20;
```

---

## Redis

Redis (Remote Dictionary Server) é um banco de dados em memória que suporta múltiplas estruturas de dados. É extremamente rápido — operações em sub-milissegundo.

### Estruturas de Dados Principais

**String:** tipo mais simples. Cache de objetos, contadores.

```redis
SET saldo:conta-001 "1500.00" EX 300   # expira em 5 minutos
GET saldo:conta-001
INCR contador:transacoes:hoje           # incremento atômico
```

**Hash:** mapa de campos e valores. Ideal para objetos.

```redis
HSET sessao:user-abc nome "Herik" papel "ADMIN" criada_em "2024-01-15"
HGET sessao:user-abc nome
HGETALL sessao:user-abc
```

**List:** lista ordenada de strings. Filas, histórico recente.

```redis
LPUSH fila:notificacoes "msg-001"   # inserir no início
RPOP fila:notificacoes              # remover do fim (fila FIFO)
LRANGE historico:user-abc 0 9       # últimos 10 itens
```

**Set:** conjunto sem duplicatas. Controle de unicidade, tags.

```redis
SADD processados:hoje "key-abc" "key-def"
SISMEMBER processados:hoje "key-abc"   # verifica existência em O(1)
```

**Sorted Set (ZSet):** set com score numérico. Rankings, leaderboards, filas com prioridade.

```redis
ZADD ranking:clientes 95.5 "cliente-001"
ZADD ranking:clientes 88.0 "cliente-002"
ZRANGE ranking:clientes 0 9 REV WITHSCORES   # top 10
```

---

### Casos de Uso em Sistemas Financeiros

**Rate limiting:** limitar requisições por cliente.

```java
public boolean verificarRateLimit(String clienteId) {
    String key = "rate:" + clienteId;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) {
        redisTemplate.expire(key, Duration.ofMinutes(1));
    }
    return count <= 100; // máximo 100 req/min
}
```

**Idempotência distribuída:** evitar reprocessamento de eventos em múltiplos pods.

```java
public boolean marcarComoProcessado(String idempotencyKey) {
    Boolean inserted = redisTemplate.opsForValue()
        .setIfAbsent(idempotencyKey, "1", Duration.ofHours(24));
    return Boolean.TRUE.equals(inserted);
}
```

**Session/Token storage:** tokens JWT invalidados antes do TTL.

```java
public void invalidarToken(String jti, long expiresIn) {
    redisTemplate.opsForValue()
        .set("blacklist:" + jti, "1", Duration.ofSeconds(expiresIn));
}
```

---

## Referências

- [Redis Documentation](https://redis.io/docs/) — guia oficial
- [High Performance MySQL](https://www.oreilly.com/library/view/high-performance-mysql/9781492080503/) — O'Reilly
- [Use The Index, Luke](https://use-the-index-luke.com/) — guia prático de índices
- [Caching Strategies](https://aws.amazon.com/caching/best-practices/) — AWS Best Practices

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
