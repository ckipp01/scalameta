package scala.meta
package internal
package trees

import scala.collection.mutable
import org.scalameta.internal.MacroHelpers

object CommonNamerMacros {

  private val quasiName = "Quasi"

}

trait CommonNamerMacros extends MacroHelpers {
  import c.universe._

  lazy val TreeClass = tq"_root_.scala.meta.Tree"
  lazy val QuasiClass = tq"_root_.scala.meta.internal.trees.Quasi"
  lazy val ClassifierClass = tq"_root_.scala.meta.classifiers.Classifier"
  lazy val ArrayClassMethod = q"_root_.scala.meta.internal.trees.`package`.arrayClass"
  lazy val ClassOfMethod = q"_root_.scala.Predef.classOf"
  lazy val TokensClass = tq"_root_.scala.meta.tokens.Tokens"
  lazy val AstAnnotation = tq"_root_.scala.meta.internal.trees.ast"
  lazy val PositionClass = tq"_root_.scala.meta.inputs.Position"
  lazy val PositionModule = q"_root_.scala.meta.inputs.Position"
  lazy val PointClass = tq"_root_.scala.meta.inputs.Point"
  lazy val PointModule = q"_root_.scala.meta.inputs.Point"
  lazy val OriginClass = tq"_root_.scala.meta.internal.trees.Origin"
  lazy val OriginModule = q"_root_.scala.meta.internal.trees.Origin"

  def mkClassifier(name: TypeName): List[Tree] = {
    val q"..$classifierBoilerplate" = q"""
      private object sharedClassifier extends $ClassifierClass[$TreeClass, $name] {
        def apply(x: $TreeClass): _root_.scala.Boolean = x.isInstanceOf[$name]
      }
      implicit def ClassifierClass[T <: $TreeClass]: $ClassifierClass[T, $name] = {
        sharedClassifier.asInstanceOf[$ClassifierClass[T, $name]]
      }
    """
    classifierBoilerplate
  }

  private val quasiTypeName = TypeName(CommonNamerMacros.quasiName)
  def isQuasiClass(cdef: ClassDef) = cdef.name.toString == CommonNamerMacros.quasiName

  def mkQuasi(
      name: TypeName,
      parents: List[Tree],
      paramss: List[List[ValDef]],
      extraAbstractDefs: List[Tree],
      extraStubs: String*
  ): ClassDef = {
    val qmods = Modifiers(NoFlags, TypeName("meta"), List(q"new $AstAnnotation"))
    val qname = quasiTypeName
    val qparents = tq"$name" +: tq"$QuasiClass" +: parents.map({
      case Ident(name) => Select(Ident(name.toTermName), quasiTypeName)
      case Select(qual, name) => Select(Select(qual, name.toTermName), quasiTypeName)
      case unsupported => c.abort(unsupported.pos, "implementation restriction: unsupported parent")
    })

    val qstats = mutable.ListBuffer[Tree]()
    qstats += q"""
      def pt: _root_.java.lang.Class[_] = {
        $ArrayClassMethod($ClassOfMethod[$name], this.rank)
      }
    """

    val stub = {
      val unsupportedUnquotingPosition = "unsupported unquoting position"
      val unsupportedSplicingPosition = "unsupported splicing position"
      val message =
        q"if (this.rank == 0) $unsupportedUnquotingPosition else $unsupportedSplicingPosition"
      q"throw new $UnsupportedOperationException($message)"
    }
    val stubbedMembers = new mutable.HashSet[String]
    def markStubbedMemberName(name: TermName): Boolean = stubbedMembers.add(name.toString)
    def addStubbedMemberWithName(name: TermName): Unit =
      if (markStubbedMemberName(name)) qstats += q"def $name = $stub"
    def addStubbedOverrideMember(tree: ValOrDefDefApi): Unit =
      if (markStubbedMemberName(tree.name)) {
        val stat = tree match {
          case x: ValDefApi =>
            q"override def ${x.name} = $stub"
          case x: DefDefApi =>
            q"override def ${x.name}[..${x.tparams}](...${x.vparamss}) = $stub"
          case _ => c.abort(tree.pos, s"Can't stub, not a 'val' or 'def': $tree")
        }
        qstats += stat
      }

    paramss.foreach(_.foreach(x => addStubbedMemberWithName(x.name)))
    extraStubs.foreach(x => addStubbedMemberWithName(TermName(x)))
    val qcopyParamss = paramss.map(_.map { case ValDef(mods, name, tpt, _) =>
      q"val $name: $tpt = this.$name"
    })
    qstats += q"def copy(...$qcopyParamss): $name = $stub"

    extraAbstractDefs.foreach {
      case x: ValOrDefDefApi if x.mods.hasFlag(Flag.ABSTRACT) || x.rhs.isEmpty =>
        addStubbedOverrideMember(x)
      case _ =>
    }

    q"$qmods class $qname(rank: $IntClass, tree: $TreeClass) extends ..$qparents { ..$qstats }"
  }
}
