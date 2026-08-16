# Pet Adoption API — matching rescued animals with adopters

[![CI](https://github.com/pedrorivanunes/pet-adoption-api/actions/workflows/ci.yml/badge.svg)](https://github.com/pedrorivanunes/pet-adoption-api/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

A REST API for running pet adoptions: shelters and private guardians list
animals, adopters describe how they live and what they are looking for, and the
service ranks the pairs by a **compatibility score** that it can explain factor
by factor. Adoption is a flow, not a field — applying, deciding, transferring
guardianship and following up for six months afterwards are all modelled
explicitly.

> **Backend only.** This repository is a REST API, not a full-stack
> application. There is no frontend here and that is deliberate: the point is
> the backend engineering — modelling, authorization, integration tests against
> a real database, reproducible infrastructure.

---

## About

The domain came from a university group project, but the code did not: this is a
rebuild from an empty directory, and the design decisions below were made on
purpose rather than inherited.

Three things carry most of the weight:

**Authorization comes from relationships, not from a global role.** An
organization is not a user — it has no login. Who may act on a shelter's behalf
is decided by a membership row, so the same person can administer one shelter,
work as staff at another, and have a personal adopter profile, with no rule
having to special-case them.

**Only people adopt.** Organizations are the supply side. When a shelter takes
an animal in, that is a transfer of custody — a different event, recorded on the
animal's timeline. Collapsing both into a polymorphic "adopter" is what makes
every adoption rule sprout exceptions.

**The score explains itself.** A ranking that says `87` and nothing else cannot
be checked and cannot be justified to the person adopting. Every result carries
its breakdown: which factor, how many points, and why.

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 4.1 |
| Persistence | Spring Data JPA + Hibernate 7 |
| Database | PostgreSQL 18 |
| Migrations | Flyway |
| Security | Spring Security 7 (OAuth2 Resource Server, JWT HS256) |
| Tests | JUnit 5 + Testcontainers + JaCoCo |
| Build | Maven (via wrapper) |
| Infrastructure | Docker + Docker Compose |
| CI | GitHub Actions |

## Running it

You need **Docker** and **Docker Compose**. Java and Maven are not required to
run the application — the build happens inside the image.

```bash
docker compose up --build
```

The API comes up on `http://localhost:8080`. Compose starts the application only
after Postgres answers `pg_isready`, so there is no arbitrary sleep in the
middle.

```bash
curl http://localhost:8080/actuator/health
```

### Local development

To run only the database in Docker and the application from source:

```bash
docker compose up -d db
```

The application **will not start without `JWT_SECRET`** (32 characters
minimum). That is deliberate: failing at boot with a clear message beats coming
up and signing tokens with an example secret nobody chose.

```bash
JWT_SECRET=a-local-secret-of-at-least-32-characters ./mvnw spring-boot:run
```

### Sample data

Compose starts with the `demo` profile active, which seeds a full scenario: a
shelter with an ADMIN and a STAFF member, five animals with varied health
conditions, a private guardian, two adopters with completed profiles, one
pending application, and an adoption from four months ago with a follow-up in
progress.

Every account uses the password `demo-password`:

| Account | Role in the scenario |
|---|---|
| `ana@example.com` | ADMIN of Four Paws Shelter |
| `bruno@example.com` | STAFF at the shelter |
| `carla@example.com` | adopter with a pending application |
| `diego@example.com` | adopter who already completed an adoption |
| `elisa@example.com` | private guardian |

The seed runs **through the application services**, not through `INSERT`s: data
seeded that way cannot exist in a state the API would reject. An integration
test boots the `demo` profile and checks the scenario, because a sample database
nobody verifies rots quietly.

For an empty database: `SPRING_PROFILES_ACTIVE=default docker compose up`.

## The API

Authentication:

| Method | Route | Access |
|---|---|---|
| `POST` | `/api/auth/register` | public |
| `POST` | `/api/auth/login` | public |
| `GET` | `/api/users/me` | authenticated |
| `GET` | `/api/users/me/pets` | authenticated |
| `GET` · `PUT` | `/api/users/me/adopter-profile` | authenticated (PUT is idempotent) |

Pets — the catalogue is public, everything else needs a claim on the animal:

| Method | Route | Access |
|---|---|---|
| `GET` | `/api/pets?status=&species=&page=&size=` | public |
| `GET` | `/api/pets/{id}` | public |
| `POST` | `/api/pets` | authenticated (owner: you, or an organization of yours) |
| `PUT` · `DELETE` | `/api/pets/{id}` | the owner, or a member of the owning organization |

Organizations — public to read, membership decides who writes:

| Method | Route | Access |
|---|---|---|
| `GET` | `/api/organizations` · `/api/organizations/{id}` | public |
| `GET` | `/api/organizations/{id}/pets` | public |
| `POST` | `/api/organizations` | authenticated (the creator becomes ADMIN) |
| `PUT` · `DELETE` | `/api/organizations/{id}` | ADMIN of the organization |
| `GET` | `/api/organizations/{id}/members` | member of the organization |
| `POST` · `PUT` · `DELETE` | `/api/organizations/{id}/members[/{userId}]` | ADMIN of the organization |

Traceability and health:

| Method | Route | Access |
|---|---|---|
| `GET` | `/api/pets/{id}/history` | **public** |
| `POST` | `/api/pets/{id}/history` | whoever manages the pet |
| `GET` · `POST` | `/api/pets/{id}/health-records` | whoever manages the pet |

Post-adoption follow-up:

| Method | Route | Access |
|---|---|---|
| `POST` | `/api/pets/{id}/followups` | whoever **handed the animal over** |
| `GET` | `/api/pets/{id}/followup-report` | whoever handed over **or** whoever adopted |

Adoption — applying belongs to the adopter, deciding belongs to whoever cares
for the pet:

| Method | Route | Access |
|---|---|---|
| `GET` | `/api/adoptions/matches` | authenticated, with an adopter profile |
| `POST` | `/api/adoptions/applications` | authenticated, with an adopter profile |
| `GET` | `/api/adoptions/applications/me` | authenticated |
| `GET` | `/api/adoptions/applications/{id}` | the applicant, or whoever manages the pet |
| `GET` | `/api/pets/{id}/applications` | whoever manages the pet |
| `POST` | `/api/adoptions/applications/{id}/approve` · `/reject` | whoever manages the pet |
| `POST` | `/api/adoptions/applications/{id}/cancel` | the applicant themselves |

`GET /api/pets/{id}/applications` doubles as the animal's **compatibility
report**: the applications come back ranked by affinity.

### Postman

[`postman/pet-adoption-api.postman_collection.json`](postman/pet-adoption-api.postman_collection.json)
covers the whole API in 33 requests. Import it and start at **Authentication →
Login**: the token is stored automatically in a collection variable and reused
by every other request. Change the `email` variable to act as someone else and
watch the permission rules respond.

## Compatibility scoring

The calculation lives in `CompatibilityCalculator` — a pure function, no
database and no HTTP, tested as a table of cases.

| Category | Situation | Points |
|---|---|---|
| Species | matches / differs from the one wanted | **+20 / −20** |
| Breed | matches / differs | **+10 / −10** |
| Size | matches / differs | **+10 / −10** |
| Sex | matches / differs | **+5 / −5** |
| Health | animal needs special care **and** adopter accepts | +10 |
| Health | animal needs special care **and** adopter declines | −10 |
| Health | healthy animal **and** adopter open to special care | +5 |
| Health | healthy animal **and** that is exactly what the adopter wants | +10 |
| Health | chronic illness accepted / declined | +10 / −10 |
| Health | no chronic illness, as the adopter wants | +5 |
| Social | sociable animal **and** adopter already has animals | +5 |
| Social | animal needs constant care **and** adopter has time | +5 |
| 🚫 | animal **does not get along** with others **and** adopter has animals | **blocker** |
| 🚫 | animal needs constant care **and** adopter has **no** time | **blocker** |

Three rules matter more than the numbers:

**A blocker is not a low score, it is elimination.** Both blocking cases concern
the animal's welfare, and no sum of points offsets them — so they come back in a
field of their own rather than as a very negative number. A pair can score high
and still be eliminated.

**An undeclared preference neither scores nor penalises.** Someone who never
said they wanted a dog should not be penalised for being offered a cat. The same
goes for missing data on the animal: an unrecorded breed, common in rescues,
does not count against it. And `goodWithOtherAnimals` being null means
*unknown*, which is different from *does not get along* — only a confirmed
negative eliminates.

**A snapshot on the application, a live calculation in the search.** An
application stores the score from the moment it was made, so editing preferences
tomorrow does not retroactively rewrite what a decision was based on. The
"I want to adopt" search recalculates every time, because there the point is to
reflect the catalogue as it is now.

## Design decisions

**The schema belongs to Flyway; Hibernate only validates it.** `ddl-auto:
validate` turns entity/table drift into a boot failure instead of an obscure
error on the first request. No migration uses `IF NOT EXISTS` — a migration is
deterministic, and drift should fail loudly.

**"One pet at a time" is a partial unique index, not an `if`.** The rule that a
person pursues one animal at a time lives in a unique index on
`adopter_user_id` **filtered by `status = 'PENDING'`**, so past applications do
not block new ones. The service checks too, but only to return a comprehensible
message: check-then-insert in code loses to two simultaneous requests, and the
index does not.

**Approving an application is an event, not a field edit.** Hence
`POST /{id}/approve` rather than a status `PATCH`: in a single transaction the
application is decided, the pet becomes adopted, the adoption enters the
history, guardianship transfers, and the other applicants are rejected with a
reason. Split into separate calls, there would be a window where the pet reads
as adopted while people are still waiting for an answer — or two approvals for
the same animal.

**The animal's journey is modelled as intervals, not events.** The domain
question is "where has it been and for how long", and duration comes out of an
interval. The start date is supplied rather than derived from `created_at`,
because a rescue almost always predates the record. Recording a new stay closes
the previous one in the same operation, so the timeline has no gaps and no
overlaps.

**JWT validation is the resource server's job, not a hand-written filter.**
Signature, expiry and claims are checked by Spring Security. A hand-rolled
filter on the critical authentication path is where silent bugs hide — a
misplaced `catch` there turns an invalid token into granted access. This code
only *issues* tokens.

**Public routes match one asterisk, not two.** `/api/pets/**` would expose any
sub-route added later, including one that should have been private. The patterns
use `/api/pets/*`, which matches only the resource level. "My pets" lives at
`/api/users/me/pets`, outside the public tree, rather than at `/api/pets/me`
where it would depend on rule ordering not to leak.

**No generic `Exception` handler.** An `@ExceptionHandler(Exception.class)` runs
ahead of Spring's default resolvers and swallows the 401 for bad credentials,
the 403 for denied access and the 400 for malformed JSON, returning 500 for all
of them. Framework errors stay with the framework; the handler covers domain
exceptions only, in RFC 7807 format.

**An organization is never left without an administrator.** The last ADMIN
cannot demote or remove themselves. Without that guard the organization goes on
existing with nobody able to administer it, and there is no way back through the
API.

## Project layout

```
src/main/java/com/petadoption/api/
├── domain/          entities, enums, and the compatibility calculator
├── repository/      Spring Data interfaces
├── service/         business rules and authorization helpers
├── security/        JWT config, token issuing, user details
├── web/             controllers, DTOs, RFC 7807 error handling
└── demo/            the demo-profile seeder

src/main/resources/db/migration/    V1–V5, applied by Flyway
src/test/java/                      105 tests, unit and integration
postman/                            versioned request collection
```

Dependencies point one way: `controller → service → repository → database`. A
full hexagonal architecture would solve problems an API this size does not have,
and the cost would show up in every trivial change.

## Tests

105 tests. They need a running Docker daemon: the integration suite starts a
real PostgreSQL through Testcontainers rather than an in-memory stand-in, so
migrations, constraints, partial indexes and `ON DELETE` behaviour are exercised
as they actually are in production.

```bash
./mvnw verify
```

That command runs everything and enforces the coverage floor. The split is
deliberate: rules that are pure logic — the compatibility calculator, species
normalisation, ownership checks — are unit tested without Spring, while rules
that have no life outside the database, such as pending-application uniqueness
and the cascade of an approval, are tested against the real thing rather than
against mocked repositories.

Tokens in the integration tests are obtained by going through the actual
register and login endpoints. A token forged inside a test would only prove the
forging works.

Coverage currently sits at about **96% instruction** and **84% branch**; the
build fails below 92%/78%. The floor is set under the current number on purpose
— it should catch a change that guts the tests, not nag about refactoring.

## Continuous integration

Two jobs on every push and pull request:

- **build and test** — runs `./mvnw verify` on a runner with a Docker daemon, so
  the Testcontainers suite runs exactly as it does locally. Publishes the JaCoCo
  report as an artifact, and the surefire reports when something fails.
- **image and compose stack** — builds the image, brings the whole stack up,
  waits for the health probe, then checks that the seeded catalogue answers and
  that a protected route still refuses an anonymous caller. The image build
  skips tests by necessity (no Docker daemon inside a build), so without this
  job nothing would prove the container actually runs.

## Known limitations and next steps

**OpenAPI documentation.** springdoc, which would generate the specification
from the controllers, is on 2.8.6 — the Spring Boot 3 line. There is no release
for Boot 4, and it depends on Jackson 2 while this project runs Jackson 3;
forcing the dependency breaks the build. The options are to write the
specification by hand and pin it to the code with a test comparing declared
routes against what Spring actually maps, or to wait for springdoc to catch up.
Until then the Postman collection and the tables above are the API
documentation.

**A period with nobody responsible.** The schema allows a stay with no
custodian — time on the street before a rescue. The API always assigns one, so
today that case is only reachable by writing directly to the database.

**Transfer of custody between organizations.** One shelter passing an animal to
another is recordable as a stay, but does not move guardianship. Doing it
properly needs a propose-and-accept flow, since two parties are involved.

## Academic context

The domain scenario originated in a group assignment for a *Software Engineering*
course, which asked for a full-stack application. This repository
is a backend-only rebuild written afterwards, from scratch, to take the same
domain further than the assignment required — integration tests against a real
database, authorization driven by relationships, reproducible infrastructure and
CI.

## License

[MIT](LICENSE)
