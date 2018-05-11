lazy val root = (project in file("."))
  .settings(
    name := """akka-streams-examples""",
    version := "1.0",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.12",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.12" % Test,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    ),
    scalaVersion := "2.12.6",
    parallelExecution in Test := false
  )

