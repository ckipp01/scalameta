package scala.meta.tests
package trees

import munit._
import scala.meta._

class TreeSuite extends FunSuite {
  test("Name.unapply") {
    assert(Name.unapply(q"a").contains("a"))
    assert(Name.unapply(t"a").contains("a"))
  }
}
