package fly.play.aws

import scala.xml.Elem
import scala.xml.Text

package object acl {
  type ACLList = Seq[Grant[_]]

  object ACLList {
    def apply(xml: Elem): ACLList = {
      (xml \ "Grant").map { elem =>
        Grant(elem.asInstanceOf[Elem])
      }
    }
  }

  case class Grant[GranteeType <: Grantee](
    permission: Permission, grantee: GranteeType)

  object Grant {
    def apply(xml: Elem): Grant[_] =
      Grant(
        Permission((xml \ "Permission").text),
        Grantee((xml \ "Grantee").head.asInstanceOf[Elem]))
  }

  sealed trait Grantee

  object Grantee {
    def apply(xml: Elem): Grantee =
      xml.attribute(xml.getNamespace("xsi"), "type") match {
        case Some(Seq(Text("CanonicalUser"))) => CanonicalUser(xml)
        case Some(Seq(Text("Group"))) => Group(xml)
      }
  }

  case class CanonicalUser(id: String, displayName: String) extends Grantee

  object CanonicalUser {
    def apply(xml: Elem): CanonicalUser =
      CanonicalUser((xml \ "ID").text, (xml \ "DisplayName").text)
  }

  case class Group(uri: String) extends Grantee

  object Group {
    def apply(xml: Elem): Group =
      Group((xml \ "URI").text) match {
        case AllUsers => AllUsers
        case LogDelivery => LogDelivery
        case group => group
      }
  }

  object AllUsers extends Group("http://acs.amazonaws.com/groups/global/AllUsers")
  object LogDelivery extends Group("http://acs.amazonaws.com/groups/s3/LogDelivery")

  case object READ extends Permission
  case object WRITE extends Permission
  case object READ_ACP extends Permission
  case object WRITE_ACP extends Permission
  case object FULL_CONTROL extends Permission

  sealed trait Permission

  object Permission {
    def apply(value: String) =
      value match {
        case "READ" => READ
        case "WRITE" => WRITE
        case "READ_ACP" => READ_ACP
        case "WRITE_ACP" => WRITE_ACP
        case "FULL_CONTROL" => FULL_CONTROL
      }
  }
}