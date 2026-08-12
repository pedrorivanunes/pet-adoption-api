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
| Segurança | Spring Security 6 |
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

```bash
./mvnw spring-boot:run
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

**`open-in-view` desligado.**
Com ele ligado, a sessão do Hibernate fica aberta durante a serialização da
resposta e esconde lazy loading — as queries N+1 aparecem só em produção.

---

## Status

Em construção. O que já está de pé:

- [x] Fundação: build, Docker, Compose, CI, configuração
- [x] Schema núcleo (usuários, papéis, organizações, vínculos, pets)
- [x] Teste de integração validando as migrations em banco limpo
- [ ] Autenticação e autorização (JWT)
- [ ] CRUD de pets, usuários e organizações
- [ ] Perfil e preferências do adotante
- [ ] Fluxo de adoção (candidatura → decisão → adoção efetivada)
- [ ] Histórico de rastreabilidade
- [ ] Cálculo de compatibilidade e busca ranqueada
- [ ] Acompanhamento pós-adoção
- [ ] Documentação OpenAPI e coleção Postman
