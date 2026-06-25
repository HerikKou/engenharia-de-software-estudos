# 06 · Segurança

Segurança não é uma feature adicionada no final — é uma propriedade construída ao longo de todo o desenvolvimento. Este módulo cobre os fundamentos que qualquer engenheiro backend precisa dominar: a diferença entre autenticação e autorização, como JWT funciona internamente e os fluxos do OAuth2.

---

## Autenticação vs Autorização

São conceitos distintos e frequentemente confundidos.

| Conceito         | Pergunta que responde         | Exemplo                                      |
|------------------|-------------------------------|----------------------------------------------|
| **Autenticação** | *Quem é você?*                | Usuário fornece email + senha; sistema valida identidade |
| **Autorização**  | *O que você pode fazer?*      | Usuário autenticado tenta deletar registro; sistema verifica se tem permissão |

**Fluxo típico:**

```
1. Usuário envia credenciais (POST /auth/login)
2. Sistema AUTENTICA → valida que o usuário existe e a senha está correta
3. Sistema emite token com claims de identidade e permissões
4. Usuário faz requisição com token (GET /contas/123)
5. Sistema AUTORIZA → verifica se o token tem permissão para acessar /contas/123
```

> **Erro comum:** autenticar mas não autorizar — sistemas que verificam "você está logado?" mas não verificam "você pode fazer isso?".

---

## JWT (JSON Web Token)

JWT é um padrão aberto (RFC 7519) para transmitir informações de forma compacta e verificável entre partes.

### Estrutura

Um JWT tem três partes separadas por `.`:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInJvbGVzIjpbIlVTRVIiXSwiZXhwIjoxNzA1MzI4MDAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

```
Header.Payload.Signature
```

**Header:** algoritmo de assinatura e tipo do token.

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload (claims):** dados do token. Nunca armazene informações sensíveis aqui — é apenas codificado em Base64, não criptografado.

```json
{
  "sub": "user-123",           // subject: identificador do usuário
  "iss": "auth.meusistema.com", // issuer: quem emitiu
  "iat": 1705241600,            // issued at: quando foi emitido
  "exp": 1705328000,            // expiration: quando expira
  "roles": ["USER", "ADMIN"],
  "jti": "uuid-único"           // JWT ID: identificador único do token
}
```

**Signature:** garante que o token não foi adulterado.

```
HMACSHA256(
  base64url(header) + "." + base64url(payload),
  secret_key
)
```

---

### JWT vs Sessions

| Critério              | JWT (Stateless)                              | Sessions (Stateful)                        |
|-----------------------|----------------------------------------------|--------------------------------------------|
| Estado no servidor    | Nenhum — token é autocontido                 | Session armazenada no servidor ou Redis    |
| Escalabilidade        | Fácil — qualquer instância valida o token    | Requer session store compartilhada         |
| Invalidação           | Complexa — requer blacklist                  | Simples — apaga a session                  |
| Tamanho               | Maior (token carrega claims)                 | Menor (apenas um ID de session)            |
| Segurança             | Payload visível (apenas codificado em Base64)| Dados ficam no servidor, não expostos      |
| Microserviços         | Ideal — sem estado compartilhado             | Problemático — todos precisam da session store |

**Vantagens do JWT:** stateless, escala horizontalmente sem configuração extra, ideal para microserviços e APIs públicas.  
**Desvantagens do JWT:** invalidação antes do TTL requer infraestrutura adicional (blacklist no Redis); token comprometido é válido até expirar.

**Vantagens de Sessions:** revogação imediata e simples; dados sensíveis ficam no servidor.  
**Desvantagens de Sessions:** requer session store compartilhada em múltiplas instâncias (Redis/banco); acoplamento à infraestrutura.

---

### Como JWT Funciona na Prática

```
1. POST /auth/login { email, senha }
         │
         ▼
2. Servidor valida credenciais, gera JWT assinado
         │
         ▼
3. Cliente recebe: { "accessToken": "eyJ...", "expiresIn": 3600 }
         │
         ▼
4. Cliente envia em todas as requisições:
   Authorization: Bearer eyJ...
         │
         ▼
5. Servidor valida assinatura + expiração → extrai claims → autoriza
```

---

### Implementação com Spring Security

```java
// Gerando o token
@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    public String gerarToken(Usuario usuario) {
        return Jwts.builder()
            .subject(usuario.getId())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000)) // 1h
            .claim("roles", usuario.getRoles())
            .signWith(getSigningKey())
            .compact();
    }

    public Claims extrairClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}

// Filtro de autenticação
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Claims claims = jwtService.extrairClaims(token);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            claims.getSubject(), null,
            mapRolesToAuthorities(claims.get("roles", List.class))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
```

---

### Boas Práticas com JWT

**Access Token + Refresh Token:**

```
Access Token:  vida curta (15–60 min) → usado nas requisições
Refresh Token: vida longa (7–30 dias) → usado para renovar o access token
```

Isso evita que um token comprometido seja válido por muito tempo.

**Invalidação antes do TTL:** JWT é stateless — sem estado no servidor. Para invalidar antes da expiração (logout, revogação), mantenha uma blacklist no Redis.

```java
public void invalidarToken(String jti, long remainingSeconds) {
    redisTemplate.opsForValue()
        .set("blacklist:jwt:" + jti, "1", Duration.ofSeconds(remainingSeconds));
}

public boolean estaInvalidado(String jti) {
    return redisTemplate.hasKey("blacklist:jwt:" + jti);
}
```

**O que não colocar no payload:**
- Senhas (mesmo hash)
- Dados pessoais sensíveis (CPF, cartão)
- Informações que mudam frequentemente

---

## OAuth2

OAuth2 é um protocolo de autorização (não autenticação) que permite que uma aplicação acesse recursos em nome de um usuário, sem que o usuário compartilhe suas credenciais.

### Participantes

| Papel                  | Descrição                                                        |
|------------------------|------------------------------------------------------------------|
| **Resource Owner**     | O usuário dono dos dados                                         |
| **Client**             | A aplicação que quer acessar os dados                            |
| **Authorization Server** | Autentica o usuário e emite tokens (Google, GitHub, Keycloak) |
| **Resource Server**    | A API que protege os recursos (sua aplicação backend)            |

---

### Authorization Code Flow (mais seguro)

O fluxo mais usado para aplicações web e mobile que envolvem usuário interativo.

```
1. Usuário clica "Login com Google"

2. Client redireciona para Authorization Server:
   GET https://accounts.google.com/o/oauth2/auth
       ?client_id=...
       &redirect_uri=https://meuapp.com/callback
       &response_type=code
       &scope=profile email

3. Usuário autentica no Google e autoriza

4. Google redireciona de volta:
   GET https://meuapp.com/callback?code=AUTH_CODE

5. Client troca o código por tokens (server-to-server):
   POST https://oauth2.googleapis.com/token
   { code, client_id, client_secret, redirect_uri }

6. Authorization Server retorna:
   { access_token, refresh_token, expires_in }

7. Client usa access_token para acessar a API
```

---

### Client Credentials Flow

Para comunicação máquina-a-máquina (M2M), sem usuário interativo. Comum em microserviços.

```
ServiceA ──▶ Authorization Server: { client_id, client_secret, grant_type: client_credentials }
          ◀── { access_token, expires_in }
          ──▶ ServiceB: Authorization: Bearer access_token
```

```java
// Spring Security OAuth2 Client (M2M)
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService) {

    OAuth2AuthorizedClientProvider provider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);
    manager.setAuthorizedClientProvider(provider);

    return manager;
}
```

---

### OpenID Connect (OIDC)

OAuth2 é sobre autorização. OIDC é uma camada sobre OAuth2 que adiciona autenticação — retorna um `id_token` (JWT) com informações do usuário.

```
OAuth2 → "Você pode acessar esses recursos"
OIDC   → "Você pode acessar esses recursos E o usuário é X, email Y, nome Z"
```

**Scope `openid`:** adicionar `openid` ao escopo transforma um fluxo OAuth2 em OIDC.

```
scope=openid profile email
```

---

### Configuração no Spring Boot

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://accounts.google.com
          # Spring valida automaticamente: assinatura, expiração, issuer
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            );

        return http.build();
    }
}
```

---

## Referências

- [JWT.io](https://jwt.io/) — debugger e documentação
- [OAuth 2.0 RFC 6749](https://www.rfc-editor.org/rfc/rfc6749) — especificação oficial
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/) — documentação oficial
- *OAuth 2 in Action* — Justin Richer e Antonio Sanso

---

*Módulo mantido por [Herik Kato](https://github.com/HerikKou)*
