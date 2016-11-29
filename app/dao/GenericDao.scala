package dao

import scala.concurrent._
import ExecutionContext.Implicits.global
import play.api.db.slick.HasDatabaseConfigProvider
import slick.driver.JdbcProfile
import scala.concurrent.Future
import generated.Tables._
import profile.api._

/**
  * Identifyable base for all Model types, it is also a Product
  * @tparam PK Primary key type
  */
trait Entity[PK] {
  //------------------------------------------------------------------------
  // public
  //------------------------------------------------------------------------
  def copy(id : PK) : Entity[PK]

  //------------------------------------------------------------------------
  // members
  //------------------------------------------------------------------------
  val id : PK
}

/**
  * Identifyable table for all Table types
  * @tparam PK Primary key type
  */
trait IdentifyableTable[PK]  {
  //------------------------------------------------------------------------
  // members
  //------------------------------------------------------------------------
  def id : slick.lifted.Rep[PK]
}

/**
  * Generic DAO implementation
  */
class GenericDao[T <: Table[E] with IdentifyableTable[PK], E <: Entity[PK], PK: BaseColumnType](tableQuery: TableQuery[T]) extends HasDatabaseConfigProvider[JdbcProfile] {
  //------------------------------------------------------------------------
  // public
  //------------------------------------------------------------------------
  /**
    * Returns the row count for this Model
    * @return the row count for this Model
    */
  def count(): Future[Int] = db.run(tableQuery.length.result)

  //------------------------------------------------------------------------
  /**
    * Returns the matching entity for the given id
    * @param id identifier
    * @return the matching entity for the given id
    */
  def findById(id: PK): Future[Option[E]] = db.run(tableQuery.filter(_.id === id).result.headOption)

  //------------------------------------------------------------------------
  /**
    * Returns all entities in this model
    * @return all entities in this model
    */
  def findAll(): Future[Seq[E]] = db.run(tableQuery.result)

  //------------------------------------------------------------------------
  /**
    * Returns newly created entity with updated id
    * @param entity entity to create, input id is ignored
    * @return newly created entity with updated id
    */
  def create(entity: E): Future[E] = {
    val insertQuery = tableQuery returning tableQuery.map(_.id) into ((row, id) => row.copy(id = id))
    val action = insertQuery += entity
    db.run(action)
  }

  //------------------------------------------------------------------------
  /**
    * Returns number of inserted entities
    * @param entities to be inserted
    * @return number of inserted entities
    */
  def create(entities: Seq[E]): Future[Unit] = db.run(tableQuery ++= entities).map(_ => ())

  //------------------------------------------------------------------------
  /**
    * Updates the given entity and returns a Future
    * @param update Entity to update (by id)
    * @return returns a Future
    */
  def update(update: E): Future[Unit] = {
    db.run(tableQuery.filter(_.id === update.id).update(update)).map(_ => ())
  }

  //------------------------------------------------------------------------
  /**
    * Deletes the given entity by Id and returns a Future
    * @param id The Id to delete
    * @return returns a Future
    */
  def delete(id: PK): Future[Unit] = db.run(tableQuery.filter(_.id === id).delete).map(_ => ())
}
