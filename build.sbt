name := "lise.bot"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies ++= {
  val akkaV = "2.4.8"

  Seq(
    "com.github.mukel" %% "telegrambot4s" % "v1.2.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "org.scalikejdbc" %% "scalikejdbc" % "2.4.1",
    "org.scalikejdbc" %% "scalikejdbc-config" % "2.4.1",
    "org.postgresql" % "postgresql" % "9.4.1208.jre7",
    "com.zaxxer" % "HikariCP" % "2.3.3",
    "org.liquibase" % "liquibase-core" % "3.0.5",
    "com.twitter" % "twitter-text" % "1.13.4",
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "org.scalatra" %% "scalatra" % "2.3.1"

  )
}

mainClass in Compile := Some("press.lis.lise.Bot")

flywayUrl := "jdbc:postgresql://localhost:5432/lise"

flywayUser := "lise_root"

flywayPassword := "root"

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

maintainer in Docker := "eliseealex"

packageName in Docker := "eliseealex/lise-bot"