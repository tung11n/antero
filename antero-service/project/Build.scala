import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions, additionalLibs}
 
object AnteroServiceKernelBuild extends Build {
  val Organization = "TKTTY"
  val Version      = "0.1"
  val ScalaVersion = "2.10.2"

  lazy val anteroServiceKernel = Project(
    id = "antero-service",
    base = file("."),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies ++= Dependencies.anteroServiceKernel,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      outputDirectory in Dist := file("target/antero-service"),
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
 
  val anteroServiceKernel = Seq(
    akkaKernel, akkaSlf4j, logback
  )
}
 
object Dependency {
  // Versions
  object V {
    val Akka      = "2.2.3"
  }
 
  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % V.Akka
  val akkaSlf4j  = "com.typesafe.akka" %% "akka-slf4j"  % V.Akka
  val logback    = "ch.qos.logback"    % "logback-classic" % "1.0.0"
}
