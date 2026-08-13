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
| `GET` · `PUT` | `/api/users/me/adopter-profile` | autenticado (PUT é idempotente) |

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

Histórico e saúde do animal:

| Método | Rota | Acesso |
|---|---|---|
| `GET` | `/api/pets/{id}/history` | **público** |
| `POST` | `/api/pets/{id}/history` | quem administra o pet |
| `GET` · `POST` | `/api/pets/{id}/health-records` | quem administra o pet |

Acompanhamento pós-adoção:

| Método | Rota | Acesso |
|---|---|---|
| `POST` | `/api/pets/{id}/followups` | quem **entregou** o animal |
| `GET` | `/api/pets/{id}/followup-report` | quem entregou **ou** quem adotou |

Adoção — candidatar-se é do adotante, decidir é de quem cuida do pet:

| Método | Rota | Acesso |
|---|---|---|
| `POST` | `/api/adoptions/applications` | autenticado, com perfil de adotante |
| `GET` | `/api/adoptions/applications/me` | autenticado |
| `GET` | `/api/adoptions/applications/{id}` | candidato ou quem administra o pet |
| `GET` | `/api/pets/{id}/applications` | quem administra o pet |
| `POST` | `/api/adoptions/applications/{id}/approve` · `/reject` | quem administra o pet |
| `POST` | `/api/adoptions/applications/{id}/cancel` | o próprio candidato |
| `GET` | `/api/adoptions/matches` | autenticado, com perfil de adotante |

`GET /api/pets/{id}/applications` é também o **relatório de compatibilidade** do
animal: as candidaturas vêm ranqueadas por afinidade.

```bash
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"name":"Ana","email":"ana@example.com","password":"senha-super-secreta"}'
```

```bash
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"ana@example.com","password":"senha-super-secreta"}'
```

### Base de exemplo

O Compose sobe com o perfil `demo` ativo, que popula a base com um cenário
completo — um abrigo com ADMIN e STAFF, cinco animais com condições variadas,
uma tutora particular, duas adotantes com perfil preenchido, uma candidatura
pendente e uma adoção de quatro meses atrás com acompanhamento em andamento.

Todas as contas usam a senha `senha-de-demonstracao`:

| Conta | Papel no cenário |
|---|---|
| `ana@exemplo.br` | ADMIN do Abrigo Quatro Patas |
| `bruno@exemplo.br` | STAFF do abrigo |
| `carla@exemplo.br` | adotante com candidatura pendente |
| `diego@exemplo.br` | adotante que já concluiu uma adoção |
| `elisa@exemplo.br` | tutora particular |

O seed é criado **pelos serviços da aplicação**, não por `INSERT`s: dado semeado
assim não consegue existir em estado que a API recusaria. E um teste de
integração sobe o perfil `demo` e confere o cenário — base de exemplo que
ninguém verifica apodrece em silêncio.

Para uma base vazia: `SPRING_PROFILES_ACTIVE=default docker compose up`.

### Coleção Postman

[`postman/pet-adoption-api.postman_collection.json`](postman/pet-adoption-api.postman_collection.json)
— 33 requisições cobrindo toda a API. Importe e comece por **Autenticação →
Login**: o token é salvo automaticamente numa variável da coleção e usado pelas
demais requisições. Troque a variável `email` para operar como outra pessoa e
ver as regras de permissão respondendo.

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

## Compatibilidade

O cálculo vive em `CompatibilityCalculator` — uma função pura, sem banco e sem
HTTP, testada por tabela de casos.

| Categoria | Situação | Pontos |
|---|---|---|
| Espécie | igual / diferente da desejada | **+20 / −20** |
| Raça | igual / diferente | **+10 / −10** |
| Porte | igual / diferente | **+10 / −10** |
| Sexo | igual / diferente | **+5 / −5** |
| Saúde | animal exige cuidados especiais **e** adotante aceita | +10 |
| Saúde | animal exige cuidados especiais **e** adotante não aceita | −10 |
| Saúde | animal saudável **e** adotante aberto a cuidados especiais | +5 |
| Saúde | animal saudável **e** adotante procura exatamente isso | +10 |
| Saúde | doença crônica aceita / recusada | +10 / −10 |
| Saúde | sem doença crônica, como o adotante procura | +5 |
| Social | animal sociável **e** adotante já tem animais | +5 |
| Social | animal exige cuidados constantes **e** adotante tem tempo | +5 |
| 🚫 | animal **não convive** com outros **e** adotante já tem animais | **impeditivo** |
| 🚫 | animal exige cuidados constantes **e** adotante **não tem** tempo | **impeditivo** |

**Impeditivo não é pontuação baixa, é eliminação.** Os dois casos acima dizem
respeito ao bem-estar do animal, e nenhuma soma de pontos os compensa — por isso
saem num campo separado do score, e não como um número muito negativo. Um par
pode ter score alto e ainda assim estar eliminado.

**Preferência não declarada não pontua nem penaliza.** Quem não disse que queria
um cão não deve ser penalizado por receber um gato: a pessoa apenas não opinou.
O mesmo vale para dado ausente no animal — raça indefinida, comum em resgatados,
não conta contra ele. E `goodWithOtherAnimals` nulo significa "não se sabe", que
é diferente de "não convive": só a negativa confirmada elimina.

**O score é explicável.** O resultado carrega a decomposição — cada fator com
categoria, pontos e motivo legível. Um ranking que diz "50" e nada mais é
impossível de conferir e impossível de justificar a quem está adotando.

**Retrato na candidatura, cálculo ao vivo na busca.** A candidatura grava o score
do momento em que foi feita: se a pessoa editar as preferências amanhã, o que
embasou a decisão não muda retroativamente. Já o "quero adotar" recalcula sempre,
porque ali o objetivo é refletir o catálogo de agora.

**O relatório cobre quem se candidatou, não a base inteira de adotantes.** Perfil
de adotante tem moradia, filhos e quantas pessoas moram na casa — informação que
um abrigo só tem motivo de ver quando aquela pessoa procurou por aquele animal.

**Candidatura com impeditivo é sinalizada, não recusada.** Fica registrada com a
marca, para quem cuida do animal decidir com a informação à vista: pode haver
contexto que o cadastro não captura.

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

**"Um pet por vez" é um índice único parcial, não um `if`.**
A regra de que cada pessoa busca um animal de cada vez vive num índice único
sobre `adopter_user_id` **filtrado por `status = 'PENDING'`** — candidaturas
passadas não bloqueiam novas. O serviço também confere, mas só para devolver
mensagem compreensível: verificar-e-depois-inserir em código perde para duas
requisições simultâneas, e o índice não perde.

**Aprovar uma candidatura é um evento, não a edição de um campo.**
Por isso é `POST /{id}/approve` e não um `PATCH` de status: numa transação só, a
candidatura é decidida, o pet vira adotado, a adoção entra no histórico e os
demais candidatos são recusados com justificativa. Quebrado em chamadas
separadas, existiria uma janela com o pet já adotado e gente ainda esperando
resposta — ou duas aprovações para o mesmo animal.

**A trajetória do animal é modelada como intervalos, não como eventos.**
A pergunta do domínio é "onde esteve e por quanto tempo" — duração se extrai de
um intervalo, não de um instante. A data de início é informada e não derivada de
`created_at`, porque o resgate quase sempre antecede o cadastro no sistema.
Registrar uma permanência nova encerra a anterior na mesma operação: a linha do
tempo não tem buraco nem sobreposição porque o encerramento é consequência da
abertura, não uma segunda chamada que alguém pode esquecer.

**Adotar é virar tutor.**
Aprovar transfere a tutoria do pet ao adotante e abre a permanência no lar
adotivo. Por isso a adoção guarda quem *entregou* o animal: sem esse registro se
perderia justamente quem tem o dever de acompanhar os seis meses seguintes — e
registrar acompanhamento não pode depender de administrar o pet, senão seria
dar ao adotante a caneta para atestar o próprio acompanhamento.

**O histórico público não identifica tutores pessoas físicas.**
Saber que o animal passou por um abrigo conhecido dá confiança à adoção;
organização é entidade pública e aparece pelo nome. Já a pessoa que o abrigou em
lar temporário aparece apenas como "houve um tutor". A ficha de saúde, essa, não
é pública em nenhuma hipótese: detalhe clínico não é vitrine.

**O relatório mostra os meses sem contato, não só a contagem.**
Vinte visitas no primeiro mês e silêncio nos cinco seguintes somam vinte, e não
são acompanhamento. Meses que ainda não chegaram não contam como falha.

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
- [x] Perfil e preferências do adotante
- [x] Fluxo de adoção (candidatura → decisão → adoção efetivada)
- [x] Cálculo de compatibilidade, busca ranqueada e relatório
- [x] Histórico de rastreabilidade e ficha de saúde
- [x] Acompanhamento pós-adoção com relatório dos 6 meses
- [x] Base de exemplo populada e verificada por teste
- [x] Coleção Postman versionada

### Em aberto

**Documentação OpenAPI.** O springdoc, que geraria a especificação a partir dos
controllers, está na versão 2.8.6 — linha do Spring Boot 3. Não existe release
para o Boot 4, e ele depende do Jackson 2, enquanto este projeto roda Jackson 3.
Forçar a dependência quebraria o build. As alternativas são escrever a
especificação à mão e prendê-la ao código com um teste que compare as rotas
declaradas com as que o Spring realmente mapeia, ou esperar o springdoc alcançar
o Boot 4. Enquanto isso, a coleção Postman e as tabelas de endpoints acima são a
documentação da API.

**Período sem responsável no histórico.** O schema permite uma permanência sem
guarda — o tempo de rua antes do resgate. A API sempre atribui um responsável,
então esse caso hoje só é alcançável por escrita direta no banco.

**Transferência de guarda entre organizações.** Um abrigo passar um animal a
outro é registrável como permanência, mas não move a tutoria: isso pediria um
fluxo de proposta e aceite, já que envolve duas partes.
