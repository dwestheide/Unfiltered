package unfiltered.request

import org.specs._

object ParamsSpecJetty extends unfiltered.spec.jetty.Served with ParamsSpec {
  def setup = { _.filter(unfiltered.filter.Planify(intent)) }
}
object ParamsSpecNetty extends unfiltered.spec.netty.Served with ParamsSpec {
  def setup = { p => 
    new unfiltered.netty.Server(p, unfiltered.netty.Planify(intent)) 
  }
}
trait ParamsSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}
  import QParams._

  import dispatch._

  /** Used for extract test */
  object Number extends Params.Extract("number", Params.first ~> Params.int)

  def intent[T]: unfiltered.Unfiltered.Intent[T] = {
    case UFPath("/basic", Params(params, _)) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }

    case POST(UFPath("/extract", Params(Number(num, _), _))) =>
      ResponseString(num.toString)

    case POST(UFPath("/extract",_)) =>
      ResponseString("passed")

    // QParam test paths:

    case GET(UFPath("/int", Params(params, _))) =>
      val expected = for {
        even <- lookup("number") is(int(_ => ()))
      } yield ResponseString(even.get.toString)
      expected(params) orFail { fails =>
        BadRequest ~> ResponseString(
          fails map { _.name  } mkString ","
        )
      }

    case GET(UFPath("/even", Params(params, _))) => 
      val expected = for {
        even <- lookup("number") is(int(in => "%s is not a number".format(in))) is 
          (pred { (_: Int) % 2 == 0} { i => "%d is odd".format(i) }) is(required("missing"))
        whatever <- lookup("what") is(required("bad"))
      } yield ResponseString(even.get.toString)
      expected(params) orFail { fails =>
        BadRequest ~> ResponseString(
          fails map { fail => fail.name + ":" + fail.error } mkString ","
        )
      }
    
    case GET(UFPath("/str", Params(params, _))) => 
      val expected = for {
        str <- lookup("param") is(int(_ => 400)) is(optional[Int,Int]) // note: 2.8 can infer type on optional
        req <- lookup("req") is(required(400))
      } yield ResponseString(str.get.getOrElse(0).toString)
      expected(params) orFail { fails =>
        BadRequest ~> Status(fails.head.error) ~> ResponseString("fail")
      }
  }
  
  "Params basic map" should {
    "map query string params" in {
      Http(host / "basic" <<? Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "map post params" in {
      Http(host / "basic" << Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
  }
  "Params Extract matcher" should {
    "match and return number" in {
      Http(host / "extract" << Map("number" -> "8") as_str) must_=="8"
    }
    "pass on a non-number" in {
      Http(host / "extract" << Map("number" -> "8a") as_str) must_== "passed"
    }
  }
  "Params Query expression" should {
    "return a number" in {
      Http(host / "int" <<? Map("number" -> "8") as_str) must_=="8"
    }
    "not match on non-number" in {
      Http.when(_ == 400)(host / "int" <<? Map("number" -> "8a") as_str) must_=="number"
    }
    "return even number" in {
      Http(host / "even" <<? Map("number"->"8","what"->"foo") as_str) must_=="8"
    }
    "fail on non-number" in {
      Http.when(_ == 400)(
        host / "even" <<? Map("number"->"eight", "what"->"") as_str
      ) must_== "number:eight is not a number"
    }
    "fail on odd number" in {
      Http.when(_ == 400)(host / "even" <<? Map("number" -> "7") as_str) must_=="number:7 is odd,what:bad"
    }
    "fail on not present" in {
      Http.when(_ == 400)(host / "even" as_str) must_=="number:missing,what:bad"
    }
    "return zero if param no int" in {
      Http(host / "str" <<? Map("req"->"whew") as_str) must_=="0"
    }
    "fail 400 if param not an int" in {
      Http.when(_ == 400)(host / "str" <<? Map("param" -> "one", "req"->"whew") as_str) must_=="fail"
    }
    "return optional param if an int" in {
      Http(host / "str" <<? Map("param"->"2","req"->"whew") as_str) must_=="2"
    }
  }
}