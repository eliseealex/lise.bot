name := "lise.bot"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies ++= {
  val akkaV = "2.4.11"

  Seq(
    "com.github.mukel" %% "telegrambot4s" % "v1.2.2",

    "org.slf4j" % "slf4j-api" % "1.7.21",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",

    "org.scalikejdbc" %% "scalikejdbc" % "2.4.1",
    "org.scalikejdbc" %% "scalikejdbc-config" % "2.4.1",
    "org.postgresql" % "postgresql" % "9.4.1208.jre7",
    "com.zaxxer" % "HikariCP" % "2.3.3",
    "org.liquibase" % "liquibase-core" % "3.0.5",

    "com.twitter" % "twitter-text" % "1.13.4",

    "org.iq80.leveldb" % "leveldb" % "0.7",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

    "com.google.guava" % "guava" % "19.0",

    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-persistence" % akkaV

  )
}

mainClass in Compile := Some("press.lis.lise.bot.Bot")

flywayUrl := "jdbc:postgresql://localhost:5432/lise"

flywayUser := "lise_root"

flywayPassword := "root"

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

maintainer in Docker := "eliseealex"

packageName in Docker := "eliseealex/lise-bot"
