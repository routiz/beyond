package beyond.engine.javascript.lib

import beyond.engine.javascript.JSArray
import beyond.engine.javascript.JSFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import play.api.mvc.Result
import play.api.mvc.Results.Ok

object ScriptableResponse {
  def jsConstructor(context: Context, args: JSArray, constructor: JSFunction, inNewExpr: Boolean): ScriptableResponse = {
    var result = args(0).asInstanceOf[String]
    // FIXME: Support other HTTP status codes.
    new ScriptableResponse(Ok(result))
  }
}

class ScriptableResponse(val result: Result) extends ScriptableObject {
  def this() = this(null)

  override def getClassName: String = "ResponseInternal"
}
