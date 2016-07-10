# Lise.bot

Bot-reminder.

## It uses
And contains examples of usage:
* [Scala telegram bot api](https://github.com/mukel/telegrambot4s) for bot engine;
* [Flyway](https://flywaydb.org/) for migration;
* [Twitter text](https://github.com/twitter/twitter-text) for hashtag extraction;
* [Typesafe config](https://github.com/typesafehub/config) for configuration;
* [Sbt native packager](http://www.scala-sbt.org/sbt-native-packager/) to publish docker images;
* [Docker](https://www.docker.com/) for deployment ([docker repository](https://hub.docker.com/r/eliseealex/lise-bot/));

## Roadmap
This bot should accept your messages, parse hashtags and let your

## Installation
To use this bot locally you will need:

* Install java 8;
* _Optional_ Set up IntelliJ Idea (tip: you can use [EAP](https://confluence.jetbrains.com/display/IDEADEV/EAP) for free);
* Clone this project and install scala and sbt;
* _Optional_ Tip: use IntelliJ Idea Grep Console to colorize logs;
* Install and set up Postgres (read more in below);
* Create bot and specify token with `-Dbot.token=TOKEN` VM-option.

### Docker publishing

This application uses [sbt native plugin](http://www.scala-sbt.org/sbt-native-packager/) to package jar and docker image.

You can publish your application to docker hub using:

```
sbt docker:publish
```

You should have docker installed and make `docker login` before.

### Deploy

You can run bot locally from docker repository simply:
```bash

 sudo docker run --net=host eliseealex/lise-bot:1.0-SNAPSHOT \
   -Dbot.token=TOKEN \
   -Ddb.default.user=DB_USER \
   -Ddb.default.password=DB_PASS
   -Ddb.default.url=DB_URL

```

Hint: [use explicit tag](https://medium.com/@mccode/the-misunderstood-docker-tag-latest-af3babfd6375#.noul4k4q5).

### Postgres set up
You will need new database `lise`, new user `lise_root` that have privileges to this database:

```

# create database lise;
CREATE DATABASE

# create user lise_root with password 'root';
CREATE ROLE

# GRANT ALL privileges ON DATABASE lise TO lise_root;
GRANT

```

### Migration

We are using flyway for migration.

To initialize or migrate database use:
```
sbt flywayMigrate
```

To get info about migration:
```
sbt flywayInfo
```

To your migrations:
```
sbt flywayClean
```

_Be careful you should clean with the same scripts that you initialized._

**Don't commit production settings, instead use:**
```
sbt flywayMigrate -Dflyway.user=prodUser -Dflyway.password=prodPassword -Dflyway.url=prodUrl
```

[Read more](https://flywaydb.org/documentation/sbt/).