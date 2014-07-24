package beyond.engine.javascript.lib.database

import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.annotations.JSGetter
import reactivemongo.bson.BSONDocument
import reactivemongo.bson.BSONObjectID

object ScriptableDocument {
  // This constructor is used internally. Users are not allowed to construct an instance directly.
  // A user must get a ScriptableDocument from either ScriptableCollection.find() or ScriptableCollection.findOne().
  def jsConstructor(context: Context, args: Array[AnyRef], constructor: Function, inNewExpr: Boolean): ScriptableDocument = {
    val fields = args(0).asInstanceOf[Seq[Field]]
    val currentValueInDB = args(1).asInstanceOf[BSONDocument]
    new ScriptableDocument(fields, currentValueInDB)
  }
}

class ScriptableDocument(fields: Seq[Field], currentValueInDB: BSONDocument) extends ScriptableObject {
  def this() = this(Seq.empty, BSONDocument.empty)

  override val getClassName: String = "Document"

  @JSGetter
  def getObjectID: String =
    currentValueInDB.getAs[BSONObjectID]("_id")
      .getOrElse(throw new NoSuchElementException("ObjectID is not exists"))
      .stringify
}
