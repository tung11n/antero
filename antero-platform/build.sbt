name := "antero-platform"
 
version := "0.1"
 
scalaVersion := "2.10.2"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.0",
  "org.json4s" %% "json4s-native" % "3.2.7"
)
