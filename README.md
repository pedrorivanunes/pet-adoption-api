# pet-adoption-api

REST API de uma plataforma de adoção de pets: cadastro de animais disponíveis
para adoção, organizações (ONGs e abrigos), tutores e adotantes, com cálculo de
compatibilidade entre adotante e pet.

> **Escopo: somente backend.** Este repositório é uma API REST, não uma
> aplicação full-stack — não existe frontend aqui, e isso é intencional. O foco
> é a qualidade da engenharia de backend: modelagem, segurança, testes de
> integração contra banco real e infraestrutura reprodutível.

O cenário de domínio nasceu de um trabalho acadêmico (PUCRS — Prática em
Engenharia de Software), mas este projeto é uma reconstrução do zero, com
decisões de arquitetura tomadas de propósito e não herdadas.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 4.1 |
| Persistência | Spring Data JPA + Hibernate 7 |
| Banco | PostgreSQL 18 |
| Migrations | Flyway |
| Segurança | Spring Security 7 (OAuth2 Resource Server, JWT HS256) |
| Testes | JUnit 5 + Testcontainers |
| Build | Maven (via wrapper) |
| Infra | Docker + Docker Compose |
| CI | GitHub Actions |

---

## Como rodar

Pré-requisitos: **Docker** e **Docker Compose**. Não é preciso ter Java nem
Maven instalados para subir a aplicação — o build acontece dentro da imagem.

### Aplicação + banco

```bash
docker compose up --build
```

A API sobe em `http://localhost:8080`. O Compose só inicia a aplicação depois
que o Postgres responde ao `pg_isready` — não há espera arbitrária no meio.

Verificação rápida:

```bash
curl http://localhost:8080/actuator/health
```

### Desenvolvimento local (só o banco no Docker)

```bash
docker compose up -d db
```

A aplicação **não sobe sem `JWT_SECRET`** (mínimo de 32 caracteres) — é falha
proposital, para que nenhum ambiente rode com segredo de exemplo:

```bash
JWT_SECRET=um-segredo-local-com-pelo-menos-32-caracteres ./mvnw spring-boot:run
```

### Endpoints

Autenticação:

| Método | Rota | Acesso |
|---|---|---|
| `POST` | `/api/auth/register` | público |
| `POST` | `/api/auth/login` | público |
| `GET` | `/api/users/me` | autenticado |
| `GET` | `/api/users/me/pets` | autenticado |

Pets — o catálogo é público, o resto exige permissão sobre o animal:

| Método | Rota | Acesso |
|---|---|---|
| `GET` | `/api/pets?status=&species=&page=&size=` | público |
| `GET` | `/api/pets/{id}` | público |
| `POST` | `/api/pets` | autenticado (dono: você ou uma organização sua) |
| `PUT` | `/api/pets/{id}` | dono, ou membro da organização dona |
| `DELETE` | `/api/pets/{id}` | dono, ou membro da organização dona |

Organizações — leitura pública, escrita conforme o vínculo:

| Método | Rota | Acesso |
|---|---|---|
| `GET` | `/api/organizations` · `/api/organizations/{id}` | público |
| `GET` | `/api/organizations/{id}/pets` | público |
| `POST` | `/api/organizations` | autenticado (criador vira ADMIN) |
| `PUT` · `DELETE` | `/api/organizations/{id}` | ADMIN da organização |
| `GET` | `/api/organizations/{id}/members` | membro da organização |
| `POST` · `PUT` · `DELETE` | `/api/organizations/{id}/members[/{userId}]` | ADMIN da organização |

```bash
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"name":"Ana","email":"ana@example.com","password":"senha-super-secreta"}'
```

```bash
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"ana@example.com","password":"senha-super-secreta"}'
```

### Testes

Precisam de um Docker daemon rodando: a suíte de integração sobe um PostgreSQL
real via Testcontainers, não um banco em memória.

```bash
./mvnw verify
```

---

## Arquitetura

Camadas simples e explícitas, sem cerimônia desnecessária:

```
controller  →  service  →  repository  →  banco
   (HTTP)     (regra de     (acesso a
              negócio)       dados)
```

A escolha é deliberada. Uma arquitetura hexagonal completa, com portas e
adaptadores, resolveria problemas que uma API deste tamanho não tem, e o custo
apareceria em cada alteração trivial. Camadas simples, com dependências
apontando numa direção só, entregam a mesma testabilidade sem o overhead.

---

## Decisões de projeto

Cada decisão abaixo tem um porquê — e boa parte delas existe para não repetir um
erro concreto de uma versão anterior deste mesmo sistema.

**Organização não adota; organização recebe.**
Abrigos e ONGs são o lado da oferta: cadastram animais. Quem adota é sempre
pessoa física. Quando uma organização assume um animal, isso é *transferência de
guarda* ou *resgate* — outro evento de negócio, com outras regras, registrado no
histórico de rastreabilidade. Isso elimina o adotante polimórfico (pessoa *ou*
organização) que tornava cada regra de adoção cheia de exceções.

**Quem age em nome de uma organização é sempre uma pessoa.**
Organização não tem login nem credencial. A permissão sobre os pets de uma
organização vem do vínculo (`organization_memberships`), não de um papel global.
A mesma pessoa pode administrar um abrigo e ter perfil pessoal de adotante sem
nenhum conflito.

**Uma fonte da verdade por fato.**
Não existe `admin_user_id` em `organizations` convivendo com uma tabela de
vínculos: o vínculo é a única fonte. Duas fontes para o mesmo fato divergem cedo
ou tarde.

**Papéis sem o prefixo `ROLE_`, e `hasAuthority()` em vez de `hasRole()`.**
O prefixo é convenção interna do Spring Security, não conceito do domínio.
Guardá-lo no banco convida ao bug clássico de dupla concatenação
(`ROLE_ROLE_ADMIN`), que quebra a autorização silenciosamente — sem erro, só um
403 inexplicável. Aqui o nome é guardado exato e comparado exato.

**O schema pertence ao Flyway; o Hibernate só valida.**
`ddl-auto: validate` faz divergência entre entidade e tabela falhar no boot, e
não virar erro obscuro na primeira requisição. Nenhuma migration usa
`IF NOT EXISTS`: migration é determinística, e desvio deve falhar alto.

**Chaves estrangeiras de dono usam `RESTRICT`, não `SET NULL`.**
Um pet tem exatamente um dono — pessoa ou organização, garantido por `CHECK`.
Com `SET NULL`, apagar um usuário deixaria o pet sem dono nenhum e violaria esse
mesmo `CHECK`, transformando um delete comum em erro de constraint. Com
`RESTRICT` a intenção fica explícita e o erro, previsível.

**Datas de nascimento em vez de idade em anos.**
Idade derivada nunca fica desatualizada. Como animal resgatado raramente tem
data exata, a estimativa é registrada como estimativa (`birth_date_estimated`)
em vez de fingir precisão que não existe.

**Validação de JWT pelo resource server, não por filtro próprio.**
Assinatura, expiração e claims são verificadas pelo Spring Security. Um filtro
escrito à mão no caminho crítico de autenticação é onde erros silenciosos se
escondem — e um `catch` mal colocado nele transforma token inválido em acesso
liberado. O código próprio se limita a *emitir* o token.

**Configuração de segurança tipada e validada.**
O segredo do JWT é um `@ConfigurationProperties` validado, sem valor padrão. Ler
a propriedade com `@Value("${chave:padrão}")` faz uma chave escrita errada cair
no padrão em silêncio — a aplicação sobe assinando tokens com um segredo que
ninguém escolheu. Aqui, chave errada ou ausente derruba o boot com mensagem.

**Rota pública casada com um asterisco, não com dois.**
`/api/pets/**` liberaria qualquer sub-rota criada depois — inclusive uma que
devesse ser privada. Os padrões usam `/api/pets/*`, que casa só o nível do
recurso. E "meus pets" mora em `/api/users/me/pets`, fora da árvore pública, em
vez de `/api/pets/me`, onde dependeria da ordem das regras para não vazar.

**Organização nunca fica sem administrador.**
O último ADMIN não consegue se rebaixar nem se remover. Sem essa trava, a
organização continua existindo sem ninguém que possa administrá-la, e não há
caminho de volta pela própria API.

**Sem handler genérico de `Exception`.**
Um `@ExceptionHandler(Exception.class)` roda antes dos resolvedores padrão do
Spring e engole o 401 de credencial inválida, o 403 de acesso negado e o 400 de
JSON malformado, devolvendo 500 para todos. Erros de framework ficam com o
framework; o handler trata só exceções do domínio, em formato RFC 7807.

**`open-in-view` desligado.**
Com ele ligado, a sessão do Hibernate fica aberta durante a serialização da
resposta e esconde lazy loading — as queries N+1 aparecem só em produção.

---

## Status

Em construção. O que já está de pé:

- [x] Fundação: build, Docker, Compose, CI, configuração
- [x] Schema núcleo (usuários, papéis, organizações, vínculos, pets)
- [x] Teste de integração validando as migrations em banco limpo
- [x] Autenticação JWT: cadastro, login e rota autenticada
- [x] Pets e organizações: CRUD com autorização por vínculo
- [ ] Perfil e preferências do adotante
- [ ] Fluxo de adoção (candidatura → decisão → adoção efetivada)
- [ ] Histórico de rastreabilidade
- [ ] Cálculo de compatibilidade e busca ranqueada
- [ ] Acompanhamento pós-adoção
- [ ] Documentação OpenAPI e coleção Postman
