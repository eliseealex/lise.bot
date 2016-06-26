name := "lise.bot"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.8"

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies += "com.github.mukel"             %%  "telegrambot4s"     % "v1.2.0"
libraryDependencies += "ch.qos.logback"               %   "logback-classic"   % "1.1.3"
libraryDependencies += "com.typesafe.scala-logging"   %%  "scala-logging"     % "3.1.0"
libraryDependencies += "org.slf4j"                    %   "slf4j-nop"         % "1.6.4"
