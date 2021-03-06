package beyond.engine.javascript.lib.database

import reactivemongo.bson.BSONObjectID

case class ObjectID(bson: BSONObjectID) {
  def this(id: String) = this(BSONObjectID(id))

  override val toString: String = bson.stringify
}
