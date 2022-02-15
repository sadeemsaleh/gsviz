name := """graph-viz"""
organization := ""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.1"

libraryDependencies += guice
libraryDependencies += javaJdbc
libraryDependencies += "org.postgresql" % "postgresql" % "42.1.4"
libraryDependencies += "com.github.haifengl" %% "smile-scala" % "2.4.0"
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.3"
libraryDependencies += "org.webjars" % "bootstrap" % "3.3.6"
enablePlugins(JavaAppPackaging)