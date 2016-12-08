
import slick.driver.PostgresDriver
import slick.jdbc.meta.MTable
import slick.codegen.SourceCodeGenerator
import slick.driver.PostgresDriver.backend.Database
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Generator extends App {
  val slickDriver = "slick.driver.PostgresDriver"
  val jdbcDriver = "org.postgresql.Driver"
  val url = "jdbc:postgresql://localhost:5432/exampledb?searchpath=public"
  val outputDir = "./app/"
  val pkg = "generated"
  val username = "postgres"
  val password = "postgres"

  val db = Database.forURL(url, username, password)
  val model = db.run(PostgresDriver.createModel(Some(MTable.getTables(None, None, None, Some(Seq("TABLE", "VIEW"))))))
  // customize code generator
  val codegenFuture : Future[SourceCodeGenerator] = model.map(model => new SourceCodeGenerator(model) {
    override def code = "import be.objectify.deadbolt.scala.models._\n" + super.code

    override def Table = new Table(_) {
      override def EntityType = new EntityTypeDef {
        /* This code is adapted from the `EntityTypeDef` trait's `code` method
           within `AbstractSourceCodeGenerator`.
           All code is identical except for those lines which have a corresponding
           comment above them. */
        override def code = {
          val args = columns.map(c=>
            c.default.map( v =>
              s"${c.name}: ${c.exposedType} = $v"
            ).getOrElse(
              s"${c.name}: ${c.exposedType}"
            )
          ).mkString(", ")
          if(classEnabled){
            /* `rowList` contains the names of the generated "Row" case classes we
                wish to have extend our `AutoIncEntity` trait. */
            val newParents = name match {
              case "UserRow" => parents ++ Seq("AutoIncEntity[Long, %s]".format(name))
              case "SecurityRoleRow" => parents ++ Seq("AutoIncEntity[Long, %s]".format(name), "Role")
              case "SecurityPermissionRow"  => parents ++ Seq("AutoIncEntity[Long, %s]".format(name), "Permission")
              case _ => parents
            }

            /* Use our modified parent class sequence in place of the old one. */
            val prns = (newParents.take(1).map(" extends "+_) ++ newParents.drop(1).map(" with "+_)).mkString("")
            s"""case class $name($args)$prns"""
          } else {
            s"""type $name = $types
              /** Constructor for $name providing default values if available in the database schema. */
              def $name($args): $name = {
              ${compoundValue(columns.map(_.name))}
              }
            """.trim
          }
        }
      }

      override def TableClass = new TableClassDef {
        /* This code is adapted from the `TableClassDef` trait's `code` method
           within `AbstractSourceCodeGenerator`.
           All code is identical except for those lines which have a corresponding
           comment above them. */
        override def code = {
          /* `tableList` contains the names of the generated table classes we
              wish to have extend our `IdentifyableTable` trait. */
          val newParents = name match {
            case "User" => parents :+ "IdentifyableTable[Long]"
            case "SecurityRole"  => parents :+ "IdentifyableTable[Long]"
            case "SecurityPermission" => parents :+ "IdentifyableTable[Long]"
            case _ => parents
          }

          /* Use our modified parent class sequence in place of the old one. */
          val prns = newParents.map(" with " + _).mkString("")
          val args = model.name.schema.map(n => s"""Some("$n")""") ++ Seq("\""+model.name.table+"\"")
          s"""class $name(_tableTag: Tag) extends profile.api.Table[$elementType](_tableTag, ${args.mkString(", ")})$prns {
            ${indent(body.map(_.mkString("\n")).mkString("\n\n"))}
            }
          """.trim()
        }
      }
    }
  })
  Await.result(codegenFuture, Duration.Inf).writeToFile(slickDriver, outputDir, pkg, "Tables", "Tables.scala")
}