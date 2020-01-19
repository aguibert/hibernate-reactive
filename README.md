# Hibernate RX

A reactive API for Hibernate ORM, supporting non-blocking database
drivers and a reactive style of interaction with the database.

Hibernate RX may be used in any plain Java program, but is especially
targeted toward usage in reactive environments like 
[Quarkus](https://quarkus.io/) and [Vert.x](https://vertx.io/).

_This project is still at an experimental stage._

## Building

To compile, navigate to this directory, and type:

    ./gradlew build

To publish Hibernate RX to your local Maven repository, run:

    ./gradlew publishToMavenLocal

## Running tests

To run the tests, ensure that PostgreSQL is installed on your machine.
From the command line, type the following commands:

    createdb hibernate-rx
    createuser hibernate-rx
    psql
    grant all privileges on database "hibernate-rx" to "hibernate-rx";

Finally, run `./gradlew test` from this directory.

## Dependencies

The project has been tested with

- Java 8
- PostgreSQL
- [Hibernate ORM](https://hibernate.org/orm/) 5.4.11-SNAPSHOT
- [SmallRye PostgreSQL Client](https://github.com/smallrye/smallrye-reactive-utils) 0.0.12

## Usage

Add the following dependency to your project:

    org.hibernate.rx:hibernate-rx-core:1.0.0-SNAPSHOT

To obtain an `RxSession` from a Hibernate `SessionFactory`, use:

    RxSession session = sessionFactory.unwrap(RxHibernateSessionFactory.class).openRxSession();

## Limitations

At this time, Hibernate RX does _not_ support the following features:

- transactions
- pessimistic locking via `LockMode`
- `@ElementCollection` and `@ManyToMany`
- `@OneToMany` without `mappedBy` 
- `GenerationType.IDENTITY` (autoincrement columns), and custom id 
  generation strategies
- `@NamedEntityGraph`
- transparent lazy loading
- eager select fetching, for example `@ManyToOne(fetch=EAGER) @Fetch(SELECT)`
- criteria queries

Instead, use the following supported features:

- optimistic locking with `@Version`
- `@OneToMany(mappedBy=...)` together with `@ManyToOne`
- `SEQUENCE` or `TABLE` id generation
- `@FetchProfile`
- explicit lazy loading via `RxSession.fetch(entity.association)`, which 
  returns a `CompletionStage`

Note that you should not use Hibernate RX with a second-level cache 
implementation which performs blocking IO, for example passivation to the
filesystem or distributed replication.

Currently only PostgreSQL is supported. Support for MySQL is coming soon!
