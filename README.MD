# Lise.bot

Bot-reminder.

## It uses
And contains examples of usage:
* [Scala telegram bot api](https://github.com/mukel/telegrambot4s) integration;
* [Slick](http://slick.lightbend.com/) integration;

## Roadmap
This bot should accept your messages, parse hashtags and let your

## Installation
To use this bot localy you will need:

* Install java 8;
* _Optional_ Set up IntelliJ Idea (tip: you can use [EAP](https://confluence.jetbrains.com/display/IDEADEV/EAP) for free);
* Clone this project and install scala and sbt;
* _Optional_ Tip: use IntelliJ Idea Grep Console to colorize logs;
* Install and set up Postgres (read more in below);
* Create bot and save token to `lise.bot.token` file.

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

Use `press.lis.lise.model.Init` object to initialize schema.