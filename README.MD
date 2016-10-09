# Lise.bot

Bot-reminder.

## It uses
And contains examples of usage:

* [Akka](http://akka.io/) for actors;
* [Akka persistence](http://doc.akka.io/docs/akka/2.4.11/scala/persistence.html) for persistence;
* [Akka finite state machine](http://doc.akka.io/docs/akka/2.4.11/scala/fsm.html) for contextual behavior;
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

### Logging

I use logging to check the status of my application. 
In most cases logging is the only way to find out that users are facing issues using my application

I don't want to track activity of my users at current stage. 
I use logging for debugging sakes only, so text logging is enough for me.

For text logging i use default scala stack:

- [slf4j](http://www.slf4j.org/) as a logger facade;
- [typesafe logging](https://github.com/typesafehub/scala-logging) for scala syntactic sugar above slf4j;
- [logback](http://logback.qos.ch/) as a logger backend;

#### Logging usage

This technology list might overwhelm at first, but it's easy, once you have set up everything.

To set up logging I should specify its' settings using `logback.xml` file.
I can create it in resources and logback will find it automatically.
My development settings are in `src/main/resources/logback.xml`.
My production settings are in `production-conf/logback.xml`.

To use logging i should implement `StrictLogging` trait and then i can use logger variable from it. 
String interpolation will be calculated lazily.

```scala
logger.debug(s"Message [$message] snoozed for [$duration]")
```

#### Color logs

I print my logs in console and use ANSI colors to separate different levels (ERROR from INFO).
If you use console, ANSI colors are working by default. 
If you use IntelliJ Idea, use Grep Console to enable it.

#### Production settings

In production i separate debug and error logs in different files.
Use rollback policies to zip my logs and move it to archive directory.

You can see it in `production-conf/logback.xml`.

To run and update my application in production i use docker. 
Docker images are stateless, i should expose volume from my hard drive to write logs there.
I'm using `-v` argument for it, like this:

```bash

-v ~/lise-bot/logs:/logs
-v ~/lise-bot/production-conf:/config 

```

### Deploy

You can run bot from docker repository using:

```bash

sudo docker run \
   --name=lise-bot --net=host \
   -v /path/to/lise-bot/logs:/logs \
   -v /path/to/lise-bot/journal:/journal \
   -v /path/to/lise.bot/production-conf:/config \
   eliseealex/lise-bot:1.0-SNAPSHOT \
   -Dbot.token=TOKEN \
   -Ddb.default.user=LISE_DB_USER \
   -Ddb.default.password=LISE_DB_PASS \
   -Ddb.default.url=LISE_DB_URL \
   -Dakka.persistence.journal.leveldb.dir=/journal \
   -Dlogback.configurationFile=/config/logback.xml >> lise-bot/bot.log &

```

Use `--net=host` access Postgres through localhost.
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

To provide access from localhost with password change `pb_hba.conf` to match:
```
# "local" is for Unix domain socket connections only
local   all             all                                     md5
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

#### Console utility

Or you can use [console flyway utility](https://flywaydb.org/getstarted/firststeps/commandline) with:

```
./flyway -user=prodUser -password=prodPass -url=jdbc:postgresql://localhost:5432/prodDatabase -locations=filesystem:/home/user/path/to/scripts/ migrate
```

To install it on linux:

1. [download](https://flywaydb.org/getstarted/download);
1. unarchive with `tar -xvf flyway-commandline-4.0.3-linux-x64.tar.gz`;
1. change directory to anarchived.

[Read more about flyway](https://flywaydb.org/documentation/sbt/).