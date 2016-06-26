name := "lise.bot"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies ++= Seq(
  "com.github.mukel" %% "telegrambot4s" % "v1.2.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.typesafe.slick" %% "slick" % "3.0.0",
  "org.postgresql" % "postgresql" % "9.3-1100-jdbc4",
  "com.zaxxer" % "HikariCP" % "2.3.3"
)
