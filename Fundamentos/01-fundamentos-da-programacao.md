# 01 · Fundamentos da Programação

Antes de construir sistemas distribuídos ou otimizar queries, é necessário dominar os blocos fundamentais da programação. Este módulo cobre os conceitos que aparecem em qualquer codebase Java — da representação de dados na memória às decisões de design orientadas a objetos.

---

## Tipos Primitivos

Em Java, tipos primitivos são armazenados diretamente na stack (não como objetos no heap), o que os torna mais eficientes em termos de memória e acesso.

| Tipo      | Tamanho | Intervalo                                      | Uso comum                       |
|-----------|---------|------------------------------------------------|---------------------------------|
| `byte`    | 8 bits  | -128 a 127                                     | Dados binários, streams         |
| `short`   | 16 bits | -32.768 a 32.767                               | Dados numéricos compactos       |
| `int`     | 32 bits | -2³¹ a 2³¹-1                                  | Contadores, IDs, valores padrão |
| `long`    | 64 bits | -2⁶³ a 2⁶³-1                                  | Timestamps, IDs de alta volume  |
| `float`   | 32 bits | ~±3.4 × 10³⁸ (precisão ~7 dígitos)            | Cálculos com baixa precisão     |
| `double`  | 64 bits | ~±1.8 × 10³⁰⁸ (precisão ~15 dígitos)          | Cálculos financeiros básicos    |
| `char`    | 16 bits | 0 a 65.535 (Unicode)                           | Caracteres individuais          |
| `boolean` | 1 bit   | `true` / `false`                               | Flags, condições                |

> **Atenção em contextos financeiros:** nunca use `float` ou `double` para valores monetários. Use `BigDecimal` para garantir precisão exata — erros de ponto flutuante são inaceitáveis em sistemas de pagamento.

### Boxing e Unboxing

Java oferece wrappers para cada primitivo (`Integer`, `Long`, `Double`, etc.), que permitem uso em coleções genéricas. O processo de conversão automática é chamado de **autoboxing** (primitivo → objeto) e **unboxing** (objeto → primitivo).

```java
int x = 42;
Integer y = x;        // autoboxing
int z = y;            // unboxing

// Cuidado: comparação de objetos
Integer a = 1000;
Integer b = 1000;
System.out.println(a == b);      // false (compara referências)
System.out.println(a.equals(b)); // true (compara valores)
```

> Java cacheia `Integer` entre -128 e 127 por performance — `==` pode retornar `true` nesse intervalo, mas esse comportamento não deve ser assumido. Sempre use `.equals()`.

---

## Estruturas de Dados

### List — Coleção Ordenada com Duplicatas

`List` mantém a ordem de inserção e permite elementos repetidos. É a estrutura de coleção mais usada no dia a dia.

**Principais implementações:**

| Implementação | Estrutura interna | Acesso por índice | Inserção/remoção no meio |
|---------------|-------------------|-------------------|--------------------------|
| `ArrayList`   | Array dinâmico    | O(1)              | O(n)                     |
| `LinkedList`  | Lista duplamente encadeada | O(n)   | O(1) com iterador        |

```java
List<String> transacoes = new ArrayList<>();
transacoes.add("PIX-001");
transacoes.add("PIX-002");
transacoes.add("PIX-001"); // duplicata permitida

System.out.println(transacoes.get(0)); // "PIX-001"
System.out.println(transacoes.size()); // 3
```

**Vantagens do `ArrayList`:** acesso direto por índice em O(1), menor uso de memória, melhor localidade de cache.  
**Desvantagens do `ArrayList`:** inserção/remoção no meio é O(n) — desloca todos os elementos seguintes; redimensionamento interno pode ser custoso.

**Vantagens do `LinkedList`:** inserção e remoção em O(1) com iterador posicionado; sem redimensionamento.  
**Desvantagens do `LinkedList`:** acesso por índice é O(n); maior consumo de memória (cada nó carrega dois ponteiros além do valor).

**Quando usar `ArrayList`:** na maioria dos casos — leitura frequente, iterações, tamanho dinâmico.  
**Quando usar `LinkedList`:** manipulação intensa no início/fim da lista (fila, deque).

---

### Set — Unicidade sem Garantia de Ordem

`Set` não permite elementos duplicados. Útil para verificar existência ou eliminar repetições.

| Implementação   | Ordenação         | Performance média | Observação                          |
|-----------------|-------------------|-------------------|-------------------------------------|
| `HashSet`       | Nenhuma           | O(1)              | Mais performático                   |
| `LinkedHashSet` | Ordem de inserção | O(1)              | Mantém sequência                    |
| `TreeSet`       | Ordem natural     | O(log n)          | Elementos devem ser `Comparable`    |

```java
Set<String> idempotencyKeys = new HashSet<>();
idempotencyKeys.add("key-abc");
idempotencyKeys.add("key-abc"); // ignorado silenciosamente

System.out.println(idempotencyKeys.size()); // 1
System.out.println(idempotencyKeys.contains("key-abc")); // true
```

> **Aplicação prática:** em sistemas de idempotência (como validação de transações PIX), `HashSet` é útil para verificar se uma chave já foi processada em memória.

---

### Map — Pares Chave-Valor

`Map` associa chaves únicas a valores. É essencial para lookup eficiente.

| Implementação   | Ordenação          | Performance média | Observação                     |
|-----------------|--------------------|-------------------|--------------------------------|
| `HashMap`       | Nenhuma            | O(1)              | Uso geral                      |
| `LinkedHashMap` | Ordem de inserção  | O(1)              | Iteração previsível            |
| `TreeMap`       | Ordem das chaves   | O(log n)          | Chaves devem ser `Comparable`  |

```java
Map<String, BigDecimal> saldos = new HashMap<>();
saldos.put("conta-001", new BigDecimal("1500.00"));
saldos.put("conta-002", new BigDecimal("320.50"));

BigDecimal saldo = saldos.getOrDefault("conta-003", BigDecimal.ZERO);
System.out.println(saldo); // 0
```

---

### Comparativo de Performance

| Operação           | ArrayList | LinkedList | HashSet | HashMap |
|--------------------|-----------|------------|---------|---------|
| Acesso por índice  | O(1)      | O(n)       | —       | O(1)    |
| Busca por valor    | O(n)      | O(n)       | O(1)    | O(1)    |
| Inserção no fim    | O(1)*     | O(1)       | O(1)    | O(1)    |
| Inserção no meio   | O(n)      | O(1)**     | —       | —       |
| Remoção            | O(n)      | O(1)**     | O(1)    | O(1)    |

\* Amortizado — pode ser O(n) quando o array interno é redimensionado.  
\*\* Com referência ao nó, sem busca.

---

## Programação Orientada a Objetos (OOP)

Os quatro pilares da OOP não são apenas conceitos teóricos — são ferramentas de design que, bem aplicados, produzem sistemas mais fáceis de manter, testar e evoluir.

---

### Encapsulamento

Proteger o estado interno de uma classe, expondo apenas o que é necessário. Evita que código externo dependa de detalhes de implementação.

**Vantagens:** centraliza regras de negócio na própria entidade; mudanças internas não quebram código externo; facilita testes unitários.  
**Desvantagens:** pode gerar excesso de getters/setters que violam o próprio princípio (anemic domain model); requer disciplina para não expor o estado desnecessariamente.

```java
public class ContaBancaria {
    private BigDecimal saldo; // estado protegido

    public ContaBancaria(BigDecimal saldoInicial) {
        if (saldoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Saldo inicial não pode ser negativo");
        }
        this.saldo = saldoInicial;
    }

    public void debitar(BigDecimal valor) {
        if (valor.compareTo(saldo) > 0) {
            throw new IllegalStateException("Saldo insuficiente");
        }
        this.saldo = this.saldo.subtract(valor);
    }

    public BigDecimal getSaldo() {
        return saldo; // leitura permitida, escrita não
    }
}
```

> A regra de negócio "saldo não pode ficar negativo" vive dentro da classe — não espalhada por todo o sistema.

---

### Herança

Reutilização e extensão de comportamento. Uma subclasse herda atributos e métodos da superclasse e pode sobrescrevê-los.

**Vantagens:** reutiliza código sem duplicação; permite polimorfismo via hierarquia; modela relacionamentos "é um" de forma natural.  
**Desvantagens:** acoplamento forte entre superclasse e subclasse — mudanças na superclasse podem quebrar subclasses; hierarquias profundas tornam o código difícil de entender e manter.

```java
public abstract class Transacao {
    protected String id;
    protected BigDecimal valor;
    protected LocalDateTime criadaEm;

    public abstract void processar();

    public String getId() { return id; }
}

public class TransacaoPix extends Transacao {
    private String chavePix;

    @Override
    public void processar() {
        // lógica específica de PIX
    }
}
```

> **Preferir composição sobre herança** quando o relacionamento não for genuinamente "é um". Herança cria acoplamento forte entre classes.

---

### Polimorfismo

A capacidade de tratar objetos de tipos diferentes de forma uniforme, desde que compartilhem uma interface ou superclasse comum.

**Vantagens:** código cliente desacoplado de implementações concretas; fácil de estender com novas implementações sem modificar código existente (Open/Closed Principle).  
**Desvantagens:** pode dificultar a leitura do código quando há muitas implementações — entender o comportamento real exige rastrear qual implementação está injetada.

```java
public interface ServicoNotificacao {
    void notificar(String mensagem);
}

public class EmailService implements ServicoNotificacao {
    @Override
    public void notificar(String mensagem) {
        System.out.println("Email: " + mensagem);
    }
}

public class SmsService implements ServicoNotificacao {
    @Override
    public void notificar(String mensagem) {
        System.out.println("SMS: " + mensagem);
    }
}

// código cliente não precisa saber a implementação concreta
ServicoNotificacao notificacao = new EmailService();
notificacao.notificar("Transação aprovada");
```

> Polimorfismo é a base para injeção de dependência no Spring — você injeta a interface, o container escolhe a implementação.

---

### Abstração

Modelar domínios complexos escondendo detalhes de implementação atrás de interfaces simples. O foco é no "o quê", não no "como".

**Vantagens:** reduz complexidade para quem usa a interface; permite trocar implementações sem impacto no código cliente; facilita testes com mocks.  
**Desvantagens:** abstração prematura ou mal delimitada cria interfaces genéricas demais, que não expressam o domínio com clareza.

```java
public interface AntifraudeService {
    ResultadoAnalise analisar(Transacao transacao);
}

// quem usa AntifraudeService não precisa saber se é
// ML, regras heurísticas ou uma API externa
```

---

## Referências

- *Effective Java* — Joshua Bloch (itens sobre tipos, coleções e OOP)
- [Java Collections Framework](https://docs.oracle.com/en/java/docs/specs/) — Oracle Docs
- [BigDecimal em contextos financeiros](https://docs.oracle.com/javase/8/docs/api/java/math/BigDecimal.html) — Oracle API Docs

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
