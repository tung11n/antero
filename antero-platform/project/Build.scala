import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions, additionalLibs}
 
object AnteroPlatformKernelBuild extends Build {
  val Organization = "TKTTY"
  val Version      = "0.1"
  val ScalaVersion = "2.10.2"

  lazy val anteroPlatformKernel = Project(
    id = "antero-platform",
    base = file("."),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies ++= Dependencies.anteroPlatformKernel,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      outputDirectory in Dist := file("target/antero-platform"),
      additionalLibs in Dist := file("./lib").listFiles
    )
  )
 
  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version      := Version,
    scalaVersion := ScalaVersion,
    crossPaths   := false,
    organizationName := "TKTTY, Inc.",
    organizationHomepage := Some(url("http://www.tktty.com"))
  )
  
  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
 
  )
}
 
object Dependencies {
  import Dependency._
 
  val anteroPlatformKernel = Seq(
    akkaKernel, akkaSlf4j, json4s, logback
  )
}
 
object Dependency {
  // Versions
  object V {
    val Akka      = "2.2.3"
  }
 
  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % V.Akka
  val akkaSlf4j  = "com.typesafe.akka" %% "akka-slf4j"  % V.Akka
  val json4s = "org.json4s" %% "json4s-native" % "3.2.7"
  val logback    = "ch.qos.logback"    % "logback-classic" % "1.0.0"
}
