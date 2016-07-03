name := "lise.bot"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies ++= Seq(
  "com.github.mukel" %% "telegrambot4s" % "v1.2.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.scalikejdbc" %% "scalikejdbc"       % "2.4.1",
  "org.postgresql" % "postgresql" % "9.4.1208.jre7",
  "com.zaxxer" % "HikariCP" % "2.3.3",
  "org.liquibase" % "liquibase-core" % "3.0.5"
)

flywayUrl := "jdbc:postgresql://localhost:5432/lise"

flywayUser := "lise_root"

flywayPassword := "root"

