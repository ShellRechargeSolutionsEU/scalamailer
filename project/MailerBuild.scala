import sbt._
import Keys._

import sbtrelease.ReleasePlugin._
import scala._

object ScynapseBuild extends Build {
  import Deps._

  lazy val basicSettings = seq(
    organization := "com.thenewmotion",
    description  := "ScalaMailer, easy wrapper around javax.mail based on LiftWeb util",

    scalaVersion := V.scala,
    resolvers ++= Seq(
      "Releases"  at "http://nexus.thenewmotion.com/content/repositories/releases",
      "Snapshots" at "http://nexus.thenewmotion.com/content/repositories/snapshots"
    ),

    scalacOptions := Seq(
      "-encoding", "UTF-8",
      "-unchecked",
      "-deprecation"
    )
  )

  lazy val moduleSettings = basicSettings ++ releaseSettings ++ seq(
    publishTo <<= version { (v: String) =>
      val nexus = "http://nexus.thenewmotion.com/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "snapshots-public")
      else                             Some("releases"  at nexus + "releases-public")
    },
    publishMavenStyle := true,
    pomExtra :=
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
          <distribution>repo</distribution>
        </license>
      </licenses>,
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
  )

  lazy val noPublishing = seq(
    publish := (),
    publishLocal := ()
  )

  lazy val root = Project("scalamailer", file("."))
    .settings(basicSettings: _*)
    .settings(moduleSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        logging,
        liftUtil,
        specs % "test"))
}

object Deps {
	object V {
		val scala = "2.10.0"
	}
  val specs =		"org.specs2" 	%%"specs2"     			% "1.14"
  val junit = 		"junit" 		% "junit"     			% "4.11"
  val logging = 	"com.typesafe"  %%"scalalogging-slf4j"  % "1.0.1"
  val liftUtil = 	"net.liftweb"   %%"lift-util"        	% "2.5-RC1"
}