package beyond.engine.javascript.lib

import beyond.engine.javascript.BeyondContext
import beyond.engine.javascript.BeyondContextFactory
import beyond.engine.javascript.JSArray
import beyond.engine.javascript.JSFunction
import java.lang.{ Boolean => JavaBoolean }
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.annotations.{ JSFunction => JSFunctionAnnotation }
import org.mozilla.javascript.annotations.{ JSStaticFunction => JSStaticFunctionAnnotation }
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

object ScriptableFuture {
  import com.beyondframework.rhino.RhinoConversions._

  private def executeCallback(contextFactory: ContextFactory, callback: JSFunction, callbackArgs: JSArray): AnyRef = {
    val beyondContextFactory = contextFactory.asInstanceOf[BeyondContextFactory]
    val scope = beyondContextFactory.global
    beyondContextFactory.call { context: Context =>
      callback.call(context, scope, scope, callbackArgs)
    }
  }

  @JSStaticFunctionAnnotation
  def successful(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    val newFuture = Future.successful(args(0))
    ScriptableFuture(context, newFuture)
  }

  @JSStaticFunctionAnnotation
  def sequence(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val futures: Seq[Future[AnyRef]] = args.map(_.asInstanceOf[ScriptableFuture].future).toSeq
    val newFuture: Future[JSArray] = Future.sequence(futures).map(_.toArray)
    ScriptableFuture(context, newFuture)
  }

  @JSStaticFunctionAnnotation
  def firstCompletedOf(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val futures: Seq[Future[AnyRef]] = args.map(_.asInstanceOf[ScriptableFuture].future).toSeq
    val newFuture: Future[AnyRef] = Future.firstCompletedOf(futures)
    ScriptableFuture(context, newFuture)
  }

  @JSFunctionAnnotation
  def onComplete(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val thisFuture = thisObj.asInstanceOf[ScriptableFuture]
    thisFuture.future.onComplete { futureResult =>
      val callbackArgs: JSArray = futureResult match {
        case Success(result) =>
          Array(result, new JavaBoolean(true))
        case Failure(ex: RhinoException) =>
          Array(ex.details(), new JavaBoolean(false))
        case Failure(throwable) =>
          Array(throwable.getMessage, new JavaBoolean(false))
      }
      executeCallback(context.getFactory, callback, callbackArgs)
    }
    thisFuture
  }

  @JSFunctionAnnotation
  def onSuccess(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val thisFuture = thisObj.asInstanceOf[ScriptableFuture]
    thisFuture.future.onSuccess {
      case result =>
        val callbackArgs: JSArray = Array(result)
        executeCallback(context.getFactory, callback, callbackArgs)
    }
    thisFuture
  }

  @JSFunctionAnnotation
  def onFailure(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val thisFuture = thisObj.asInstanceOf[ScriptableFuture]
    thisFuture.future.onFailure {
      case ex: RhinoException =>
        val callbackArgs: JSArray = Array(ex.details)
        executeCallback(context.getFactory, callback, callbackArgs)
      case throwable: Throwable =>
        val callbackArgs: JSArray = Array(throwable.getMessage)
        executeCallback(context.getFactory, callback, callbackArgs)
    }
    thisFuture
  }

  @JSFunctionAnnotation
  def map(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val newFuture = thisObj.asInstanceOf[ScriptableFuture].future.map { result =>
      val callbackArgs: JSArray = Array(result)
      executeCallback(context.getFactory, callback, callbackArgs)
    }

    ScriptableFuture(context, newFuture)
  }

  @JSFunctionAnnotation
  def flatMap(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val newFuture = thisObj.asInstanceOf[ScriptableFuture].future.flatMap { result =>
      val callbackArgs: JSArray = Array(result)
      executeCallback(context.getFactory, callback, callbackArgs) match {
        case futureByCallback: ScriptableFuture =>
          val promise = Promise[AnyRef]()
          futureByCallback.future.onComplete {
            case Success(success) =>
              promise.success(success)
            case Failure(throwable) =>
              promise.failure(throwable)
          }
          promise.future
        case _ =>
          throw new Exception("result.of.callback.is.not.future")
      }
    }

    ScriptableFuture(context, newFuture)
  }

  @JSFunctionAnnotation
  def filter(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val newFuture = thisObj.asInstanceOf[ScriptableFuture].future.filter { result =>
      val callbackArgs: JSArray = Array(result)
      val filterResult = executeCallback(context.getFactory, callback, callbackArgs)
      ScriptRuntime.toBoolean(filterResult)
    }

    ScriptableFuture(context, newFuture)
  }

  @JSFunctionAnnotation
  def recover(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val callback = args(0).asInstanceOf[JSFunction]
    val newFuture = thisObj.asInstanceOf[ScriptableFuture].future.recover {
      case exception: RhinoException =>
        val callbackArgs: JSArray = Array(exception.details)
        executeCallback(context.getFactory, callback, callbackArgs)
      case throwable: Throwable =>
        val callbackArgs: JSArray = Array(throwable.getMessage)
        executeCallback(context.getFactory, callback, callbackArgs)
    }

    ScriptableFuture(context, newFuture)
  }

  @JSFunctionAnnotation
  def transform(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val thisFuture = thisObj.asInstanceOf[ScriptableFuture]
    val promise = Promise[AnyRef]()
    thisFuture.future.onComplete {
      case Success(result) =>
        val callbackArgs: JSArray = Array(result)
        val callbackOnSuccess = args(0).asInstanceOf[JSFunction]
        promise.success(executeCallback(context.getFactory, callbackOnSuccess, callbackArgs))
      case Failure(exception: RhinoException) =>
        val callbackArgs: JSArray = Array(exception.details)
        val callbackOnFailure = args(1).asInstanceOf[JSFunction]
        promise.failure(new Exception(executeCallback(context.getFactory, callbackOnFailure, callbackArgs).asInstanceOf[String]))
      case Failure(throwable) =>
        val callbackArgs: JSArray = Array(throwable.getMessage)
        val callbackOnFailure = args(1).asInstanceOf[JSFunction]
        promise.failure(new Exception(executeCallback(context.getFactory, callbackOnFailure, callbackArgs).asInstanceOf[String]))
    }

    ScriptableFuture(context, promise.future)
  }

  @JSFunctionAnnotation
  def andThen(context: Context, thisObj: Scriptable, args: JSArray, function: JSFunction): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val thisFuture = thisObj.asInstanceOf[ScriptableFuture]
    val callback = args(0).asInstanceOf[JSFunction]
    val newFuture = thisFuture.future.andThen {
      case result =>
        val callbackArgs: JSArray = result match {
          case Success(result) =>
            Array(result, new JavaBoolean(true))
          case Failure(ex: RhinoException) =>
            Array(ex.details(), new JavaBoolean(false))
          case Failure(throwable) =>
            Array(throwable.getMessage, new JavaBoolean(false))
        }
        executeCallback(context.getFactory, callback, callbackArgs)
    }

    ScriptableFuture(context, newFuture)
  }

  def jsConstructor(context: Context, args: JSArray, constructor: JSFunction, inNewExpr: Boolean): ScriptableFuture = {
    implicit val executionContext = context.asInstanceOf[BeyondContext].executionContext
    val newFuture = args(0) match {
      case callback: JSFunction =>
        Future {
          val callbackArgs = Array.empty[AnyRef]
          executeCallback(context.getFactory, callback, callbackArgs)
        }
      case future: Future[_] =>
        // Cannot check Future[AnyRef] because of type erasure.
        future.asInstanceOf[Future[AnyRef]]
      case _ =>
        throw new IllegalArgumentException("type.is.not.matched")
    }
    new ScriptableFuture(newFuture)
  }

  private[lib] def apply(context: Context, future: Future[_]): ScriptableFuture = {
    val beyondContextFactory = context.getFactory.asInstanceOf[BeyondContextFactory]
    val scope = beyondContextFactory.global
    val args: JSArray = Array(future)
    context.newObject(scope, "Future", args).asInstanceOf[ScriptableFuture]
  }
}

class ScriptableFuture(val future: Future[AnyRef]) extends ScriptableObject {
  def this() = this(null)

  override def getClassName: String = "Future"
}
