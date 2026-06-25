# 08 · Infraestrutura e DevOps

Código que só roda na sua máquina não é software — é um rascunho. Este módulo cobre Docker, Docker Compose e os princípios de configuração de ambientes que tornam aplicações portáveis, reproduzíveis e prontas para produção.

---

## Docker

Docker empacota uma aplicação e todas as suas dependências em um container — uma unidade isolada e portável que roda da mesma forma em qualquer ambiente.

### VM vs Container

| Critério           | Máquina Virtual (VM)                     | Container (Docker)                        |
|--------------------|------------------------------------------|-------------------------------------------|
| Isolamento         | Completo — SO próprio por VM             | Processo isolado — compartilha o kernel do host |
| Tamanho            | GBs (inclui SO completo)                 | MBs (apenas dependências da app)          |
| Tempo de boot      | Minutos                                  | Segundos                                  |
| Overhead           | Alto (hypervisor + SO guest)             | Baixo (apenas o processo da app)          |
| Portabilidade      | Imagem pesada, lenta de mover            | Image leve, push/pull rápido              |
| Segurança          | Isolamento mais forte                    | Isolamento menor — kernel compartilhado   |
| Caso de uso        | Workloads que precisam de SO diferente   | Microserviços, deploys frequentes, CI/CD  |

**Vantagens do Docker:** inicialização rápida, imagens leves, reprodutibilidade total do ambiente, integração nativa com orquestradores (Kubernetes).  
**Desvantagens do Docker:** isolamento menos forte que VMs (kernel compartilhado); requer cuidado com segurança de imagens (vulnerabilidades em layers base); containers sem volume perdem dados ao reiniciar.

### Conceitos Fundamentais

| Conceito      | Analogia                     | Descrição                                           |
|---------------|------------------------------|-----------------------------------------------------|
| **Image**     | Receita / classe             | Template imutável com o sistema de arquivos e instruções |
| **Container** | Instância em execução        | Processo isolado criado a partir de uma image       |
| **Dockerfile**| Receita de construção        | Arquivo com instruções para construir a image       |
| **Registry**  | Repositório de images        | Docker Hub, ECR, GCR — onde images são armazenadas |
| **Volume**    | Disco externo                | Armazenamento persistente fora do container         |
| **Network**   | Rede privada                 | Comunicação entre containers                        |

---

### Dockerfile — Boas Práticas

```dockerfile
# Multi-stage build: build stage separada do runtime
# Resultado final não inclui Maven, JDK completo, código-fonte
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar pom.xml primeiro — otimiza o cache de layers
# Se só o código mudou, as dependências não são baixadas de novo
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Runtime stage: imagem mínima com apenas JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Criar usuário não-root (segurança)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar só o JAR final do build stage
COPY --from=build /app/target/*.jar app.jar

# Documentar a porta (não abre de fato — isso é do docker run ou compose)
EXPOSE 8080

# Health check interno
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

**Por que multi-stage?**

Sem multi-stage, a image final inclui Maven, JDK completo e código-fonte — facilmente 500MB–1GB. Com multi-stage, apenas JRE + JAR — geralmente 150–200MB.

---

### Comandos Essenciais

```bash
# Construir image a partir do Dockerfile no diretório atual
docker build -t minha-app:1.0 .

# Rodar container
docker run -d \
  --name minha-app \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://db:5432/app \
  minha-app:1.0

# Listar containers em execução
docker ps

# Ver logs
docker logs -f minha-app

# Entrar no container (debug)
docker exec -it minha-app /bin/sh

# Parar e remover
docker stop minha-app && docker rm minha-app

# Listar e remover images não usadas
docker images
docker image prune -f
```

---

### Layers e Cache

Cada instrução no Dockerfile cria uma layer. Docker reutiliza layers do cache se nada mudou acima.

```dockerfile
# ❌ Ineficiente: qualquer mudança no código invalida o cache das dependências
COPY . .
RUN mvn package

# ✅ Eficiente: dependências são cacheadas separadamente do código
COPY pom.xml .
RUN mvn dependency:go-offline    # layer cacheada enquanto pom.xml não mudar
COPY src ./src
RUN mvn package                  # só esta layer é reconstruída quando código muda
```

---

## Docker Compose

Docker Compose orquestra múltiplos containers localmente — define serviços, redes e volumes em um único arquivo YAML.

### Estrutura de um Sistema com Microserviços

```yaml
# docker-compose.yml
services:

  # ─── Infraestrutura ────────────────────────────────────

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    networks: [backend-net]
    healthcheck:
      test: ["CMD", "echo", "ruok", "|", "nc", "localhost", "2181"]
      interval: 10s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      zookeeper:
        condition: service_healthy
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    networks: [backend-net]
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 15s
      retries: 5

  db-transacao:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: transacao_db
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - transacao-data:/var/lib/postgresql/data
    networks: [backend-net]

  # ─── Serviços da Aplicação ─────────────────────────────

  transacao-service:
    build:
      context: ./transacao-service
      dockerfile: Dockerfile
    ports:
      - "8081:8081"
    env_file: .env
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db-transacao:5432/transacao_db
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SERVER_PORT: 8081
    depends_on:
      kafka:
        condition: service_healthy
      db-transacao:
        condition: service_started
    networks: [backend-net]
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://localhost:8081/actuator/health"]
      interval: 30s
      retries: 3

  devolucao-service:
    build:
      context: ./devolucao-service
      dockerfile: Dockerfile
    ports:
      - "8082:8082"
    env_file: .env
    environment:
      SERVER_PORT: 8082
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      kafka:
        condition: service_healthy
    networks: [backend-net]

# ─── Volumes e Redes ───────────────────────────────────────

volumes:
  transacao-data:
  devolucao-data:

networks:
  backend-net:
    driver: bridge
```

---

### Comandos Essenciais

```bash
# Subir todos os serviços (detached)
docker compose up -d

# Subir só a infraestrutura primeiro
docker compose up -d zookeeper kafka db-transacao

# Ver status de todos os serviços
docker compose ps

# Logs de um serviço específico
docker compose logs -f transacao-service

# Rebuildar e restartar apenas um serviço
docker compose up -d --build transacao-service

# Parar tudo (mantém volumes)
docker compose down

# Parar e apagar volumes (reset completo)
docker compose down -v
```

---

## Ambientes de Execução

### Variáveis de Ambiente

Credenciais e configurações sensíveis nunca vão no código ou no Dockerfile. Elas são injetadas via variáveis de ambiente.

```bash
# .env (nunca commitar no git — adicionar ao .gitignore)
DB_USER=app_user
DB_PASSWORD=super_secret_password
DATADOG_API_KEY=abc123...
JWT_SECRET=64_character_random_string_here
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

```yaml
# docker-compose.yml referencia variáveis do .env
env_file: .env
environment:
  SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
```

```java
// Spring Boot lê variáveis de ambiente automaticamente
@Value("${JWT_SECRET}")
private String jwtSecret;

// Ou via @ConfigurationProperties
@ConfigurationProperties(prefix = "app")
public record AppConfig(String jwtSecret, Duration tokenExpiry) {}
```

---

### Spring Profiles

Profiles permitem diferentes configurações para diferentes ambientes sem mudar o código.

```yaml
# application.yml — configurações comuns
spring:
  application:
    name: transacao-service

# application-local.yml — sobrescreve para local
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/transacao_db

# application-production.yml — sobrescreve para produção
spring:
  datasource:
    url: ${DATABASE_URL}
  jpa:
    show-sql: false
logging:
  level:
    root: INFO
```

```bash
# Ativar profile
SPRING_PROFILES_ACTIVE=production java -jar app.jar

# Via variável no docker-compose
environment:
  SPRING_PROFILES_ACTIVE: production
```

---

### .gitignore Essencial para Projetos Spring + Docker

```gitignore
# Credenciais e configurações sensíveis
.env
*.env.local
application-local.yml
application-secrets.yml

# Build artifacts
target/
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/

# Docker
docker-compose.override.yml

# Logs
*.log
logs/
```

---

### Estrutura de Projeto Recomendada

```
meu-sistema/
├── docker-compose.yml
├── .env.example          # template público com chaves, sem valores reais
├── .env                  # valores reais — no .gitignore
├── .gitignore
├── transacao-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── devolucao-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
└── README.md
```

---

## Referências

- [Docker Documentation](https://docs.docker.com/) — referência oficial
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/) — especificação do compose file
- [Spring Boot Docker](https://spring.io/guides/topicals/spring-boot-docker/) — guia oficial Spring
- [12-Factor App](https://12factor.net/) — princípios para aplicações cloud-native (especialmente fator III: Config)

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
