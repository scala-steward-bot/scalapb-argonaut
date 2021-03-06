package scalapb_argonaut

import scalapb.{GeneratedMessageCompanion, JavaProtoSupport}
import scalapb.e2e.repeatables.RepeatablesTest
import scalaprops.Scalaprops
import scalaprops.Property.forAll

object RepeatablesSpecJVM extends Scalaprops {
  private[this] val g = new RepeatableTestGen(UnknownFieldSetGenInstance.value)
  import g._

  val `UnknownFieldSet same as java` = {
    val javaPrinter = com.google.protobuf.util.JsonFormat.printer()
    val companion = implicitly[GeneratedMessageCompanion[RepeatablesTest]]
      .asInstanceOf[JavaProtoSupport[RepeatablesTest, com.google.protobuf.GeneratedMessageV3]]
    forAll { (v: RepeatablesTest) =>
      val scalaJson = JsonFormat.printer.print(v)
      val javaJson = javaPrinter.print(companion.toJavaProto(v))
      import argonaut.JsonParser.parse
      val x = parse(scalaJson)
      assert(x.isRight)
      x == parse(javaJson)
    }
  }
}
